# Assumptions and known limitations
1. Meridian's OpenAPI describes status updates, not an ordering guarantee. We ignore stale terminal regressions; production should reconcile event ordering with provider GET and timestamps.
2. Tokens are already created upstream. The application forwards but does not persist the token; the provider mock stores the request only in process memory.
3. HMAC-SHA256 signature is lowercase hexadecimal in this demo; the contract says HMAC but does not prescribe encoding. Confirm with provider.
4. H2 file database keeps local payments across restarts, but the in-memory mock does NOT persist. Restart the application and delete `data/` to reset both. Production would use durable provider state.
5. The mock `simulate` endpoint is intentionally unauthenticated and MUST NOT be exposed in production.
6. The implementation's `synchronized` locks work only within one JVM. For multiple instances use DB uniqueness, transactional locking and a durable outbox/in-flight operation model.
7. A create timeout leaves PDNG with no provider ID; this demo returns 502 and does not automatically recover. Production should reconcile by merchant reference / provider idempotency and retry safely, not create a second charge.
8. The create endpoint returns 202; repeated requests with same key return the same locally recorded payment even if pending.
9. Callback URL uses localhost for local demo; a real provider requires a publicly reachable HTTPS callback.
10. No automatic 22:00 UTC settlement in mock. Simulate ACSC manually after ACCP.
11. Cancellation errors do not overwrite local status. A real service must reconcile unknown cancellation outcomes.
12. In production configure secrets via environment/secret manager, authenticated APIs, rate limits, audit logging, migration scripts and a non-public H2 console.
