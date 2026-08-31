# W7 outbox recovery note

Order and outbox append share one local transaction; forced rollback leaves neither row. The single-row relay uses a pessimistic lock, publishes the stable event ID, then marks PUBLISHED. A crash after provider acknowledgement rolls back only the local status, so retry publishes the same ID again: delivery 2, W6-compatible effect 1. A mutation that skipped PENDING rows made the crash-window oracle Red before restoration.
