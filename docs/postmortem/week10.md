# Week 10 postmortem

The controlled `checkout-latency-a` incident deliberately used `COMMIT_PENDING_THEN_DROP` on requests 4, 5, and 6. This was known injection, not inferred causality. The operational exercise was to prove that the observable evidence distinguishes it from Redis admission failure.

Baseline was health 10/10, checkout terminal 10, UNKNOWN 0, and provider effects 10. During fault, health remained 10/10 while the business SLI fell to terminal 7 with UNKNOWN 3; provider effects stayed 10. The first discriminator joined each order and payment-attempt key to its provider transaction. Effects existed for all three UNKNOWN intents. W8's capacity 3, duplicate work-ID, slot return, and fail-closed regression remained Green, so the Redis hypothesis was rejected.

Recovery queried the provider by the same `X-USER-ID: 135135` and `LP-ORD-%010d` identifiers. It settled the three existing UNKNOWN intents without another create POST. The result converged to terminal 10, UNKNOWN 0, provider effects 10, duplicate dispatch 0. A blanket restart was rejected because it could erase the uncertainty trail and invite duplicate charges without changing already committed provider state.

Raw Actuator bodies are in `evidence/week10/{baseline,fault,recovery}-health.json` and matching `.prom` files. The payment state transition is asserted by `IncidentScenarioTest`; W5 verifies the real simulator wire contract separately, while W9 retains the published input/result checksums and same-JobInstance restart.

Follow-up: alert when any UNKNOWN intent remains for five minutes and include the provider-order reconciliation count; keep the second-dispatch fence executable. For an analogous login-latency incident, compare an external-auth transaction/request correlation signal with database pool pending/active saturation. Those signals distinguish lost identity-provider responses from local pool exhaustion better than health alone.

