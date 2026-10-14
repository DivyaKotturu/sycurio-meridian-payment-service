package com.example.payments.api.dto;

import com.example.payments.domain.*;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(String id, String providerPaymentId, String endToEndId, BigDecimal amount,
                              String currency, PaymentStatus status, String statusReason, Instant createdAt,
                              Instant updatedAt) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.id, p.providerPaymentId, p.endToEndId, p.amount, p.currency, p.status, p.statusReason, p.createdAt, p.updatedAt);
    }
}
