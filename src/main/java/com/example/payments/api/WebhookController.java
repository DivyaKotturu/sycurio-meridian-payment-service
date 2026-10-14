package com.example.payments.api;

import com.example.payments.api.dto.*;
import com.example.payments.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/meridian")
public class WebhookController {
    private final PaymentService service;
    private final ObjectMapper mapper;
    private final String secret;

    public WebhookController(PaymentService service, ObjectMapper mapper, @Value("${app.webhook-secret}") String secret) {
        this.service = service;
        this.mapper = mapper;
        this.secret = secret;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestHeader(value = "X-Meridian-Signature", required = false) String signature, @RequestBody String body) throws Exception {
        if (!WebhookSignature.valid(secret, body, signature))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Webhook signature invalid");
        service.webhook(mapper.readValue(body, MeridianWebhookRequest.class));
        return ResponseEntity.ok().build();
    }
}
