package com.example.payments.api.dto;

import com.example.payments.domain.PaymentStatus;

import java.time.Instant;

public record MeridianPaymentResponse(String paymentId, String endToEndId, AmountDto amount, String maskedPan,
                                      PaymentStatus status, String statusReason, Instant createdAt, Instant updatedAt) {
}
