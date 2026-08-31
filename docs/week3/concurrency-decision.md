# W3 concurrency decision

`StockService.decrease` takes a database pessimistic row lock and completes validation plus decrement in one transaction. Tests use separate ready/start/done barriers with stock 5 and request counts 8 and 12, then reconcile `success + final stock = 5` and `success + failure = requests`. A deliberate no-op decrement made both cases Red before the exact line was restored.
