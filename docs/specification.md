# Specification (written before implementation)
## Goal
Expose a merchant payment API that accepts an already-tokenised card payment, tracks asynchronous Meridian Pay statuses, and permits cancellation before settlement.
## Functional requirements
- FR1: Create payment with token, decimal amount, ISO currency and Idempotency-Key.
- FR2: Submit to Meridian `POST /payments` with `endToEndId`, nested amount, token, callbackUrl and key.
- FR3: Persist merchant and provider IDs and current provider status.
- FR4: GET current locally recorded status.
- FR5: Cancel only PDNG/ACCP by calling Meridian cancellation; no local-only cancellation.
- FR6: Verify webhook HMAC-SHA256 over exact raw body, deduplicate eventId, correlate endToEndId.
- FR7: Mock provider HTTP interface with manual status simulation.
## Status policy
PDNG -> ACCP / RJCT / CANC; ACCP -> ACSC / CANC; ACSC, RJCT, CANC terminal. Duplicate statuses accepted; stale terminal regressions ignored. Provider mock simulates authorisation/rejection and settlement.
## Out of scope
Raw card collection/tokenisation; refund endpoint; real gateway credentials; scheduled settlement; production auth/observability.
## Non-functional
Never log card tokens or persist PAN/CVV; use decimal money; provider timeout; unique idempotency key; webhook signature validation; persistent database; tests.
