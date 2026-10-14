package com.example.payments.api;

import com.example.payments.api.dto.*;
import com.example.payments.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                  @Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.create(key, request));
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping("/{id}/cancel")
    public PaymentResponse cancel(@PathVariable String id) {
        return service.cancel(id);
    }
}
