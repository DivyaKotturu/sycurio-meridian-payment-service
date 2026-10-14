package com.example.payments.api.dto;

import com.example.payments.domain.PaymentStatus;

import java.time.Instant;

public record MeridianWebhookRequest(String eventId, String paymentId, String endToEndId, PaymentStatus status,
                                     String statusReason, Instant occurredAt) {
}
