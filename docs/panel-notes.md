# Panel walkthrough (5-8 minutes)
1. **Problem:** Merchant receives card token, not PAN/CVV. Submit to Meridian, track async state, cancel before settlement.
2. **Show spec first:** Payment states PDNG, ACCP, ACSC, RJCT, CANC; settlement at 22:00 UTC is provider responsibility.
3. **Demo:** POST payment -> 202 PDNG -> mock simulate ACCP -> signed callback -> GET shows ACCP -> cancel -> CANC.
4. **Idempotency:** Repeat same POST key; local ID unchanged. Change amount with same key; 409.
5. **Security:** Webhook HMAC over raw body; do not log or persist card token; no raw card data.
6. **Race:** Cancellation can lose to settlement. Meridian is authoritative; 409 is not a successful cancellation.
7. **Timeout:** HTTP timeout is unknown outcome, not proof of failure. Production requires provider reconciliation.
8. **Trade-offs:** H2 and same-process HTTP mock chosen for quick demo; production needs PostgreSQL, migrations, distributed coordination, observability and auth.
