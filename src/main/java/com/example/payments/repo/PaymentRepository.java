package com.example.payments.repo;

import com.example.payments.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByIdempotencyKey(String key);

    Optional<Payment> findByEndToEndId(String endToEndId);
}
