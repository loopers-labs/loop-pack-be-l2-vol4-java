# Week 10 checkout-latency-a runbook

## Trigger and preservation

Trigger on checkout final-state success below 10/10 while Actuator health remains UP. Preserve the baseline/fault/recovery markers, the ten exact order IDs `LP-ORD-0000000001` through `LP-ORD-0000000010`, payment attempt keys, provider transaction IDs, raw health, and raw Prometheus bodies before changing state.

## Competing hypotheses and first discriminator

- H1: the payment provider committed but its response was lost. Query `orderId + paymentAttemptKey -> intent -> provider transaction`; expect provider effect present beside UNKNOWN.
- H2: Redis admission rejected work. Query `workId -> admission result -> PENDING count`; expect a final payment but explicit fail-closed rejection.

The first discriminator is provider effect count versus terminal payment count. Fault evidence is health 10/10, terminal 7, UNKNOWN 3, provider effects 10. Queue capacity, duplicate work ID, returned slot, and outage fail-closed remain Green, so reject H2.

## Bounded mitigation and recovery

1. Stop new work only if UNKNOWN continues increasing; do not restart the application, database, or Redis as the first action.
2. For the three UNKNOWN intents, use the same synthetic user `135135` and provider order ID to perform provider order lookup.
3. Apply `SETTLE_SUCCESS` to the existing intent. Never issue another create POST; `REJECT_SECOND_DISPATCH` is the guard.
4. Reconcile the same ten IDs to terminal 10, UNKNOWN 0, provider effects 10, duplicate dispatch 0.
5. Re-run the W5 payment, W8 queue, W9 batch restart, and W10 incident gates by their exact FQCNs.

## Stop, rollback, and re-entry

Stop if lookup returns zero or multiple provider transactions, any provider order ID differs, provider effects exceed 10, or Redis regression fails. Preserve the mismatch and leave the intent UNKNOWN; do not guess. There is no data rollback for an already committed provider effect. Re-entry requires a single authoritative provider record for the same order ID, intact raw evidence, and the second-dispatch fence enabled.

Shutdown order is pg-simulator first, then only this exercise's Redis readonly, Redis master, and MySQL services. Verify all three report zero running services.

