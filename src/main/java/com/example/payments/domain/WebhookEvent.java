package com.example.payments.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "webhook_events")
public class WebhookEvent {
 @Id
 public String eventId;
 public String paymentId;
 public Instant receivedAt;

 protected WebhookEvent() {
 }

 public WebhookEvent(String eventId, String paymentId) {
  this.eventId = eventId;
  this.paymentId = paymentId;
  this.receivedAt = Instant.now();
 }
}
