# Improvements from the initial exercise draft

- Replaced nested `final class Dto` with seven separate, descriptive request/response records.
- Validated the returned Meridian payment reference before accepting a create response.
- Validated provider cancellation response before recording `CANC`.
- Hardened webhook field validation; rejected empty provider payment IDs.
- Restricted `PDNG` transitions to those described in the supplied API (no direct settlement).
- Mock Meridian can be disabled with `MOCK_ENABLED=false`.
- Disabled H2 browser console by default.
- Added tests for settlement cancellation conflict and invalid amounts.
- Made provider URL, webhook secret and callback URL configurable using environment variables.

## Important limitations / next production steps

This remains a take-home **demo**, not a production payment gateway. In particular:

1. The synchronous local `synchronized` locks only protect one JVM. For multi-instance operation use a DB-backed claim/outbox, row-level locking, and a reconciliation worker.
2. A lost provider create response can leave a `PDNG` payment without a provider ID. The provider's `endToEndId` correlation or an idempotent replay/reconciliation process must be agreed before production. The supplied provider API does not specify a lookup by `endToEndId`.
3. A webhook that arrives before the HTTP response can correlate using `endToEndId`, but the original create thread may race with the webhook DB update. Robust production handling needs transactional compare-and-set and provider state precedence.
4. A payment may settle during cancellation. Meridian's response is authoritative, and the client should refresh/reconcile on conflict or timeout.
5. Webhook event uniqueness is DB-backed; concurrent duplicate insertions can still race, so retry/transaction handling should be hardened for multi-instance operation.
6. No real customer authentication, secrets manager, observability, provider webhook replay protection window, or scheduled 22:00 settlement job is included. The mock exposes a simulation endpoint only for demonstrations.
7. The contract specifies HMAC-SHA256 over the raw body but not the signature's wire encoding. This demo assumes lowercase hexadecimal and documents that assumption.
