package com.example.payments;

import com.example.payments.api.dto.PaymentResponse;
import com.example.payments.domain.PaymentStatus;
import com.example.payments.service.WebhookSignature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Full HTTP integration tests against the in-process Meridian mock and H2. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=18080",
                "app.base-url=http://localhost:18080",
                "app.meridian-base-url=http://localhost:18080/mock-meridian/v1",
                "app.webhook-secret=integration-test-secret",
                "app.mock-enabled=true",
                "spring.datasource.url=jdbc:h2:mem:sycurio_integration;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
class PaymentIntegrationTest {

 private static final String BASE = "http://localhost:18080";
 private static final String SECRET = "integration-test-secret";
 private static final HttpClient CLIENT = HttpClient.newHttpClient();

 @Autowired private ObjectMapper mapper;

 private record Result(int status, String body) {
  JsonNode json(ObjectMapper mapper) throws Exception {
   return body == null || body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
  }
 }

 private Result send(String method, String path, String json, String key, String signature) throws Exception {
  HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path))
          .header("Accept", "application/json");
  if (key != null) builder.header("Idempotency-Key", key);
  if (signature != null) builder.header("X-Meridian-Signature", signature);
  if (json != null) builder.header("Content-Type", "application/json");
  HttpRequest.BodyPublisher body = json == null
          ? HttpRequest.BodyPublishers.noBody()
          : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
  HttpRequest request = builder.method(method, body).build();
  HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  return new Result(response.statusCode(), response.body());
 }

 private Result post(String path, String json, String key) throws Exception {
  return send("POST", path, json, key, null);
 }

 private Result get(String path) throws Exception {
  return send("GET", path, null, null, null);
 }

 private String paymentJson(String amount, String token) {
  return "{\"token\":\"" + token + "\",\"amount\":" + amount + ",\"currency\":\"GBP\"}";
 }

 private PaymentResponse create(String amount) throws Exception {
  Result result = post("/api/v1/payments", paymentJson(amount, "tok_test_" + UUID.randomUUID()),
          UUID.randomUUID().toString());
  assertEquals(202, result.status(), result.body());
  PaymentResponse payment = mapper.readValue(result.body(), PaymentResponse.class);
  assertEquals(PaymentStatus.PDNG, payment.status());
  assertNotNull(payment.providerPaymentId());
  return payment;
 }

 private void simulate(String providerId, String status) throws Exception {
  Result result = post("/mock-meridian/v1/payments/" + providerId + "/simulate?status=" + status, null, null);
  assertEquals(200, result.status(), result.body());
 }

 private PaymentResponse current(String id) throws Exception {
  Result result = get("/api/v1/payments/" + id);
  assertEquals(200, result.status(), result.body());
  return mapper.readValue(result.body(), PaymentResponse.class);
 }

 private String webhookJson(String eventId, String providerId, String reference, String status) {
  return "{\"eventId\":\"" + eventId + "\",\"paymentId\":\"" + providerId
          + "\",\"endToEndId\":\"" + reference + "\",\"status\":\"" + status
          + "\",\"statusReason\":null,\"occurredAt\":\"" + Instant.now() + "\"}";
 }

 private Result webhook(String json, String signature) throws Exception {
  return send("POST", "/api/v1/webhooks/meridian", json, null, signature);
 }

 @Test void createsPaymentAndReadsPendingStatus() throws Exception {
  PaymentResponse payment = create("125.50");
  assertEquals(PaymentStatus.PDNG, current(payment.id()).status());
 }

 @Test void sameIdempotencyKeyReturnsSamePaymentAndDifferentBodyConflicts() throws Exception {
  String key = UUID.randomUUID().toString();
  String json = paymentJson("125.50", "tok_same");
  Result first = post("/api/v1/payments", json, key);
  Result second = post("/api/v1/payments", json, key);
  assertEquals(202, first.status(), first.body());
  assertEquals(202, second.status(), second.body());
  assertEquals(first.json(mapper).get("id").asText(), second.json(mapper).get("id").asText());
  Result conflict = post("/api/v1/payments", paymentJson("126.50", "tok_same"), key);
  assertEquals(409, conflict.status(), conflict.body());
 }

 @Test void missingAndOversizedIdempotencyKeysAreRejected() throws Exception {
  String json = paymentJson("10.00", "tok_valid");
  assertEquals(400, post("/api/v1/payments", json, null).status());
  assertEquals(400, post("/api/v1/payments", json, "x".repeat(65)).status());
 }

 @Test void invalidPaymentRequestsAreRejected() throws Exception {
  assertEquals(400, post("/api/v1/payments", paymentJson("-1.00", "tok_valid"), UUID.randomUUID().toString()).status());
  assertEquals(400, post("/api/v1/payments", paymentJson("0.00", "tok_valid"), UUID.randomUUID().toString()).status());
  assertEquals(400, post("/api/v1/payments", paymentJson("10.123", "tok_valid"), UUID.randomUUID().toString()).status());
  assertEquals(400, post("/api/v1/payments", paymentJson("10.00", ""), UUID.randomUUID().toString()).status());
  assertEquals(400, post("/api/v1/payments", "{\"token\":\"tok\",\"amount\":10,\"currency\":\"gbp\"}", UUID.randomUUID().toString()).status());
 }

 @Test void unknownPaymentReturnsNotFound() throws Exception {
  assertEquals(404, get("/api/v1/payments/does-not-exist").status());
  assertEquals(404, post("/api/v1/payments/does-not-exist/cancel", null, null).status());
 }

 @Test void unsignedAndIncorrectlySignedWebhooksAreRejected() throws Exception {
  String json = webhookJson("evt_" + UUID.randomUUID(), "mer_fake", "e2e_fake", "ACCP");
  assertEquals(401, webhook(json, null).status());
  assertEquals(401, webhook(json, "invalid-signature").status());
 }

 @Test void validWebhookUpdatesStatusAndDuplicateEventIsSafe() throws Exception {
  PaymentResponse payment = create("35.00");
  String eventId = "evt_" + UUID.randomUUID();
  String json = webhookJson(eventId, payment.providerPaymentId(), payment.endToEndId(), "ACCP");
  String signature = WebhookSignature.sign(SECRET, json);
  assertEquals(200, webhook(json, signature).status());
  assertEquals(PaymentStatus.ACCP, current(payment.id()).status());
  assertEquals(200, webhook(json, signature).status());
  assertEquals(PaymentStatus.ACCP, current(payment.id()).status());
 }

 @Test void validWebhookWithWrongProviderIdIsRejected() throws Exception {
  PaymentResponse payment = create("15.00");
  String json = webhookJson("evt_" + UUID.randomUUID(), "mer_wrong", payment.endToEndId(), "ACCP");
  assertEquals(400, webhook(json, WebhookSignature.sign(SECRET, json)).status());
  assertEquals(PaymentStatus.PDNG, current(payment.id()).status());
 }

 @Test void validWebhookWithUnknownReferenceIsRejected() throws Exception {
  String json = webhookJson("evt_" + UUID.randomUUID(), "mer_unknown", "e2e_unknown", "ACCP");
  assertEquals(404, webhook(json, WebhookSignature.sign(SECRET, json)).status());
 }

 @Test void mockAuthorisationAndCancellationWorkAndCancellationIsIdempotent() throws Exception {
  PaymentResponse payment = create("50.00");
  simulate(payment.providerPaymentId(), "ACCP");
  assertEquals(PaymentStatus.ACCP, current(payment.id()).status());
  Result cancelled = post("/api/v1/payments/" + payment.id() + "/cancel", null, null);
  assertEquals(200, cancelled.status(), cancelled.body());
  assertEquals(PaymentStatus.CANC, current(payment.id()).status());
  Result again = post("/api/v1/payments/" + payment.id() + "/cancel", null, null);
  assertEquals(200, again.status(), again.body());
  assertEquals(PaymentStatus.CANC, current(payment.id()).status());
 }

 @Test void pendingPaymentCanBeCancelled() throws Exception {
  PaymentResponse payment = create("12.00");
  Result result = post("/api/v1/payments/" + payment.id() + "/cancel", null, null);
  assertEquals(200, result.status(), result.body());
  assertEquals(PaymentStatus.CANC, current(payment.id()).status());
 }

 @Test void settledPaymentCannotBeCancelled() throws Exception {
  PaymentResponse payment = create("45.00");
  simulate(payment.providerPaymentId(), "ACCP");
  simulate(payment.providerPaymentId(), "ACSC");
  assertEquals(PaymentStatus.ACSC, current(payment.id()).status());
  assertEquals(409, post("/api/v1/payments/" + payment.id() + "/cancel", null, null).status());
  assertEquals(PaymentStatus.ACSC, current(payment.id()).status());
 }

 @Test void rejectedPaymentCannotBeCancelled() throws Exception {
  PaymentResponse payment = create("22.00");
  simulate(payment.providerPaymentId(), "RJCT");
  assertEquals(PaymentStatus.RJCT, current(payment.id()).status());
  assertEquals(409, post("/api/v1/payments/" + payment.id() + "/cancel", null, null).status());
 }

 @Test void staleWebhookDoesNotRegressSettledPayment() throws Exception {
  PaymentResponse payment = create("20.00");
  simulate(payment.providerPaymentId(), "ACCP");
  simulate(payment.providerPaymentId(), "ACSC");
  String stale = webhookJson("evt_" + UUID.randomUUID(), payment.providerPaymentId(), payment.endToEndId(), "ACCP");
  assertEquals(200, webhook(stale, WebhookSignature.sign(SECRET, stale)).status());
  assertEquals(PaymentStatus.ACSC, current(payment.id()).status());
 }

 @Test void mockRejectsInvalidStatusTransition() throws Exception {
  PaymentResponse payment = create("11.00");
  Result result = post("/mock-meridian/v1/payments/" + payment.providerPaymentId() + "/simulate?status=ACSC", null, null);
  assertEquals(409, result.status(), result.body());
 }
}
