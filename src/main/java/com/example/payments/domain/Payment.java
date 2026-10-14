package com.example.payments.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments", uniqueConstraints = {@UniqueConstraint(columnNames = "idempotencyKey"), @UniqueConstraint(columnNames = "providerPaymentId"), @UniqueConstraint(columnNames = "endToEndId")})
public class Payment {
 @Id
 public String id;
 @Column(nullable = false)
 public String idempotencyKey;
 @Column(nullable = false)
 public String requestHash;
 @Column(nullable = false)
 public String endToEndId;
 @Column(unique = true)
 public String providerPaymentId;
 @Column(nullable = false, precision = 18, scale = 2)
 public BigDecimal amount;
 @Column(nullable = false, length = 3)
 public String currency;
 @Enumerated(EnumType.STRING)
 @Column(nullable = false)
 public PaymentStatus status;
 public String statusReason;
 @Column(nullable = false)
 public Instant createdAt;
 @Column(nullable = false)
 public Instant updatedAt;
 @Version
 public long version;

 protected Payment() {
 }

 public Payment(String id, String key, String hash, String endToEndId, BigDecimal amount, String currency) {
  this.id = id;
  this.idempotencyKey = key;
  this.requestHash = hash;
  this.endToEndId = endToEndId;
  this.amount = amount;
  this.currency = currency;
  this.status = PaymentStatus.PDNG;
  this.createdAt = Instant.now();
  this.updatedAt = createdAt;
 }
}
