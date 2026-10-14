# Sycurio take-home: Meridian Pay integration (Java 21 / Spring Boot)

Small Spring Boot merchant payment orchestration service built against the **provided Meridian Pay OpenAPI** in `docs/meridian-pay-api.yaml`.

## Requirements
- Java 21, Maven 3.9+ (or Docker with Compose)
- Local port 8080 available

## Start
```bash
mvn clean test
mvn spring-boot:run
```
Or `docker compose up --build`.

Default demo configuration uses a file-backed H2 database at `./data/payments` and an in-process **HTTP mock** exposed at `/mock-meridian/v1`. No real card details or payments are processed. Disable the mock with `MOCK_ENABLED=false` and configure `MERIDIAN_BASE_URL`, `APP_BASE_URL`, `WEBHOOK_SECRET` for an external provider. The mock is not for production.

## Walkthrough (copy/paste)
```bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-order-001' \
  -d '{"token":"tok_demo_123","amount":125.50,"currency":"GBP"}'
```
Copy the returned `id` and `providerPaymentId`.

```bash
# Query merchant's stored status
curl http://localhost:8080/api/v1/payments/<id>

# Ask mock Meridian to authorise, which sends a signed webhook to merchant
curl -X POST 'http://localhost:8080/mock-meridian/v1/payments/<providerPaymentId>/simulate?status=ACCP'

# Check status again
curl http://localhost:8080/api/v1/payments/<id>

# Cancel before settlement
curl -X POST http://localhost:8080/api/v1/payments/<id>/cancel
```
For settlement, simulate `ACCP`, then `ACSC`; subsequent cancellation should return 409. For rejection, simulate `RJCT`. Duplicate creates with the same key/body return the original payment; same key with changed body returns 409.

## Merchant endpoints
| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/payments` | Submit tokenised payment, `Idempotency-Key` required |
| GET | `/api/v1/payments/{id}` | Query locally tracked status |
| POST | `/api/v1/payments/{id}/cancel` | Request void from Meridian |
| POST | `/api/v1/webhooks/meridian` | Verify HMAC signature and record status event |

## Status lifecycle
- `PDNG`: pending authorisation
- `ACCP`: authorised, can still cancel
- `ACSC`: settled, cannot cancel; refund is a separate provider API and is out of scope
- `RJCT`: rejected
- `CANC`: cancelled before settlement

The provided contract says authorised payments settle daily at 22:00 UTC. The demo **does not run a real settlement scheduler**: use the mock simulation endpoint to trigger `ACSC`.

## Design and caveats
See `docs/specification.md`, `docs/assumptions-and-limitations.md`, `docs/changes-from-v1.md`, and `docs/panel-notes.md`. In particular, payment creation with an unknown provider outcome is intentionally **not blindly retried**. The solution demonstrates key concerns but is not production-ready for multi-instance concurrency or reconciliation.

## Security
Never send raw PAN/CVV to merchant APIs. The token is forwarded only to Meridian and is not persisted in the payment table. Webhooks are HMAC verified. Use a secret manager and customer authentication in production.

## Tests
`mvn test` runs integration tests covering idempotency, callback-driven authorisation, cancellation, settled-payment cancellation conflict, invalid amount, and unsigned webhook rejection.

## Interview walkthrough
Explain the three flows: payment submission (202 pending), signed asynchronous webhook (status update), and cancellation (allowed only before settlement). Explain trade-offs around duplicate callbacks, provider timeouts, and races; see `docs/panel-notes.md`.
