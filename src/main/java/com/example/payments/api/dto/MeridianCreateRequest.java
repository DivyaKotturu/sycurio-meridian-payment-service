package com.example.payments.api.dto;

public record MeridianCreateRequest(String endToEndId, AmountDto amount, String token, String callbackUrl) {
}
