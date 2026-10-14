package com.example.payments.meridian;

import com.example.payments.api.*;
import com.example.payments.api.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class MeridianClient {
    private final RestClient client;
    private final String baseUrl;
    private final String callbackUrl;

    public MeridianClient(RestClient client, @Value("${app.meridian-base-url}") String baseUrl, @Value("${app.base-url}") String appUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.callbackUrl = appUrl + "/api/v1/webhooks/meridian";
    }

    public MeridianPaymentResponse create(String key, String endToEndId, CreatePaymentRequest request) {
        MeridianCreateRequest body = new MeridianCreateRequest(endToEndId, new AmountDto(request.amount().setScale(2).toPlainString(), request.currency()), request.token(), callbackUrl);
        return client.post().uri(baseUrl + "/payments").header("Idempotency-Key", key).body(body).retrieve().body(MeridianPaymentResponse.class);
    }

    public MeridianPaymentResponse cancel(String providerId, String key) {
        return client.post().uri(baseUrl + "/payments/" + providerId + "/cancellation").header("Idempotency-Key", key).retrieve().body(MeridianPaymentResponse.class);
    }

    public MeridianPaymentResponse get(String providerId) {
        return client.get().uri(baseUrl + "/payments/" + providerId).retrieve().body(MeridianPaymentResponse.class);
    }
}
