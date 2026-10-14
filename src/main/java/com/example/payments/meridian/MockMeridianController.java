package com.example.payments.meridian;

import com.example.payments.api.*;
import com.example.payments.api.dto.*;
import com.example.payments.domain.PaymentStatus;
import com.example.payments.service.WebhookSignature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local HTTP mock, NOT a real card processor. /simulate is test-only.
 */
@RestController
@ConditionalOnProperty(name = "app.mock-enabled", havingValue = "true")
@RequestMapping("/mock-meridian/v1")
public class MockMeridianController {
    private final Map<String, MockPayment> store = new ConcurrentHashMap<>();
    private final Map<String, String> keys = new ConcurrentHashMap<>();
    private final RestClient http;
    private final ObjectMapper mapper;
    private final String secret;

    public MockMeridianController(RestClient http, ObjectMapper mapper, @Value("${app.webhook-secret}") String secret) {
        this.http = http;
        this.mapper = mapper;
        this.secret = secret;
    }

    private static class MockPayment {
        String id;
        MeridianCreateRequest request;
        PaymentStatus status = PaymentStatus.PDNG;
        String reason;
        Instant created = Instant.now();

        MockPayment(String id, MeridianCreateRequest request) {
            this.id = id;
            this.request = request;
        }
    }

    private MeridianPaymentResponse view(MockPayment p) {
        return new MeridianPaymentResponse(p.id, p.request.endToEndId(), p.request.amount(), "************1111", p.status, p.reason, p.created, Instant.now());
    }

    @PostMapping("/payments")
    public synchronized ResponseEntity<MeridianPaymentResponse> create(@RequestHeader("Idempotency-Key") String key, @RequestBody MeridianCreateRequest request) {
        if (keys.containsKey(key)) {
            MockPayment p = store.get(keys.get(key));
            if (!p.request.equals(request)) return ResponseEntity.status(409).build();
            return ResponseEntity.status(202).body(view(p));
        }
        String id = "mer_" + UUID.randomUUID().toString().substring(0, 8);
        MockPayment p = new MockPayment(id, request);
        store.put(id, p);
        keys.put(key, id);
        return ResponseEntity.status(202).body(view(p));
    }

    @GetMapping("/payments/{id}")
    public MeridianPaymentResponse get(@PathVariable String id) {
        return view(require(id));
    }

    @PostMapping("/payments/{id}/cancellation")
    public synchronized ResponseEntity<MeridianPaymentResponse> cancel(@PathVariable String id, @RequestHeader("Idempotency-Key") String key) {
        MockPayment p = require(id);
        if (p.status == PaymentStatus.CANC) return ResponseEntity.ok(view(p));
        if (p.status != PaymentStatus.PDNG && p.status != PaymentStatus.ACCP) return ResponseEntity.status(409).build();
        p.status = PaymentStatus.CANC;
        return ResponseEntity.ok(view(p));
    }

    @PostMapping("/payments/{id}/simulate")
    public synchronized MeridianPaymentResponse simulate(@PathVariable String id, @RequestParam PaymentStatus status) throws Exception {
        MockPayment p = require(id);
        boolean allowed = (p.status == PaymentStatus.PDNG && (status == PaymentStatus.ACCP || status == PaymentStatus.RJCT)) || (p.status == PaymentStatus.ACCP && status == PaymentStatus.ACSC);
        if (!allowed)
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Mock cannot transition " + p.status + " to " + status);
        p.status = status;
        p.reason = status == PaymentStatus.RJCT ? "AM04" : null;
        MeridianWebhookRequest event = new MeridianWebhookRequest("evt_" + UUID.randomUUID(), p.id, p.request.endToEndId(), status, p.reason, Instant.now());
        String json = mapper.writeValueAsString(event);
        http.post().uri(p.request.callbackUrl()).header("X-Meridian-Signature", WebhookSignature.sign(secret, json)).contentType(MediaType.APPLICATION_JSON).body(json).retrieve().toBodilessEntity();
        return view(p);
    }

    private MockPayment require(String id) {
        MockPayment p = store.get(id);
        if (p == null) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Mock payment not found");
        return p;
    }
}
