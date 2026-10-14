package com.example.payments.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class WebhookSignature {
    private WebhookSignature() {
    }

    public static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean valid(String secret, String body, String provided) {
        if (provided == null) return false;
        return MessageDigest.isEqual(sign(secret, body).getBytes(StandardCharsets.US_ASCII), provided.getBytes(StandardCharsets.US_ASCII));
    }
}
