# W6 Kafka delivery note

One test-owned broker and topic prove RangeAssignor with two partitions and static identities A/B/C. Ordered joins and continuously polled consumers must hold `A=[0], B=[1], C=[]` for three consecutive snapshots. Closing the first consumer after recording an effect but before commit redelivers the same offset; delivery is 2 and the durable `kafka_effect_ledger` effect is 1. A mutation that reported ignored inserts as new effects made the ledger test Red before restoration.
