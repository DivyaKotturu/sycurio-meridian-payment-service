package com.example.payments.service;

import com.example.payments.api.*;
import com.example.payments.api.dto.*;
import com.example.payments.domain.*;
import com.example.payments.repo.*;
import com.example.payments.meridian.MeridianClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final WebhookEventRepository events;
    private final MeridianClient meridian;

    public PaymentService(PaymentRepository payments, WebhookEventRepository events, MeridianClient meridian) {
        this.payments = payments;
        this.events = events;
        this.meridian = meridian;
    }

    private String hash(CreatePaymentRequest r) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest((r.token() + "|" + r.amount().setScale(2).toPlainString() + "|" + r.currency()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized PaymentResponse create(String key, CreatePaymentRequest r) {
        if (key == null || key.isBlank() || key.length() > 64)
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Supply Idempotency-Key (1-64 characters)");
        String h = hash(r);
        Optional<Payment> existing = payments.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            if (!existing.get().requestHash.equals(h))
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Key already used with a different request");
            return PaymentResponse.from(existing.get());
        }
        String id = UUID.randomUUID().toString();
        String reference = "e2e_" + UUID.randomUUID().toString().replace("-", "").substring(0, 25);
        Payment p = payments.saveAndFlush(new Payment(id, key, h, reference, r.amount().setScale(2), r.currency()));
        try {
            MeridianPaymentResponse response = meridian.create(key, reference, r);
            if (response == null || response.paymentId() == null || !reference.equals(response.endToEndId()))
                throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_PROVIDER_RESPONSE", "Meridian returned an invalid payment response");
            p.providerPaymentId = response.paymentId();
            apply(p, response.status(), response.statusReason());
            return PaymentResponse.from(payments.save(p));
        } catch (RestClientException ex) { // outcome is unknown: retain local PDNG; retry/reconciliation required
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_OUTCOME_UNKNOWN", "Meridian request failed or timed out; payment remains pending. Check payment status using the same idempotency key.");
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(String id) {
        return PaymentResponse.from(find(id));
    }

    private Payment find(String id) {
        return payments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
    }

    public synchronized PaymentResponse cancel(String id) {
        Payment p = find(id);
        if (p.status == PaymentStatus.CANC) return PaymentResponse.from(p);
        if (p.status != PaymentStatus.PDNG && p.status != PaymentStatus.ACCP)
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_CANCEL", "Payment cannot be cancelled in status " + p.status);
        if (p.providerPaymentId == null)
            throw new ApiException(HttpStatus.CONFLICT, "PROVIDER_OUTCOME_UNKNOWN", "Provider payment ID is not yet known; reconcile before cancelling");
        try {
            MeridianPaymentResponse result = meridian.cancel(p.providerPaymentId, "cancel-" + p.id);
            if (result == null || !p.providerPaymentId.equals(result.paymentId()) || result.status() != PaymentStatus.CANC)
                throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_CANCEL_RESPONSE", "Meridian did not confirm cancellation");
            apply(p, result.status(), result.statusReason());
            return PaymentResponse.from(payments.save(p));
        } catch (HttpClientErrorException.Conflict ex) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_CANCEL", "Meridian rejected cancellation; payment may already be settled. Refresh status.");
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CANCEL_OUTCOME_UNKNOWN", "Cancellation result unknown; refresh/reconcile before retrying");
        }
    }

    @Transactional
    public synchronized void webhook(MeridianWebhookRequest event) {
        if (event.eventId() == null || event.eventId().isBlank() || event.paymentId() == null || event.paymentId().isBlank() || event.endToEndId() == null || event.endToEndId().isBlank() || event.status() == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT", "Missing webhook fields");
        if (events.existsById(event.eventId())) return;
        Payment p = payments.findByEndToEndId(event.endToEndId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "UNKNOWN_REFERENCE", "Unknown endToEndId"));
        if (p.providerPaymentId != null && !p.providerPaymentId.equals(event.paymentId()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_MISMATCH", "Provider ID mismatch");
        if (p.providerPaymentId == null) p.providerPaymentId = event.paymentId();
        apply(p, event.status(), event.statusReason());
        payments.save(p);
        events.save(new WebhookEvent(event.eventId(), p.id));
    }

    private void apply(Payment p, PaymentStatus incoming, String reason) {
        if (incoming == null) return;
        boolean allowed = switch (p.status) {
            case PDNG ->
                    incoming == PaymentStatus.PDNG || incoming == PaymentStatus.ACCP || incoming == PaymentStatus.RJCT || incoming == PaymentStatus.CANC;
            case ACCP ->
                    incoming == PaymentStatus.ACCP || incoming == PaymentStatus.ACSC || incoming == PaymentStatus.CANC;
            case ACSC, RJCT, CANC -> incoming == p.status;
        };
        if (allowed) {
            p.status = incoming;
            p.statusReason = reason;
            p.updatedAt = Instant.now();
        }
        // Out-of-order/stale events are acknowledged without regressing a final status.
    }
}
