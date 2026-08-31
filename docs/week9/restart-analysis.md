# Week 9 restart analysis

The job is `weeklyRankingJob`; its identifying parameter is `periodStart`. `injectFailure` is a non-identifying experiment control and is not a substitute for `run.id`.

| Attempt | JobInstance | JobExecution | Status | `reader.index` | Durable result |
|---|---|---|---|---:|---|
| initial `2026-08-17` | same instance A | execution A1 | FAILED | 2 | event-01 and event-02 |
| restart `2026-08-17` | same instance A | execution A2 | COMPLETED | 3 | duplicate event-02 adds no effect |
| fresh `2026-08-18` | new instance B | execution B1 | COMPLETED | 3 | recomputed from row 1 |

Chunk size 2 makes the first two rows and `reader.index=2` one committed boundary. The controlled exception occurs while processing row 3, before its writer call. Restart opens the reader at index 2. The writer's `(snapshot_id,event_id)` boundary is still required because a checkpoint cannot make an external writer exactly-once.

The canonical LF-terminated TSV source SHA-256 is `d80a24248fd42f959bddc497efa65afd0d3f1cab420933800140b3027bac98e1`. The final `snapshotId/productId/score/rank` SHA-256 is `cd34a2cb816c288cd704e7fdf27784c01b642b571288ec4f1a854b7a1a4a48e4`; the failed/restarted and clean fresh runs converge on it with two applied event IDs.

A late monthly-settlement input changes the frozen source window. It must use a separately identified backfill JobInstance when auditability or reader consistency matters, rather than silently reopening an instance whose source checksum has changed.
