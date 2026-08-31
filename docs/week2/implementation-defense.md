# W2 implementation defense

`OrderDiscountFacade` owns only the transaction and sequence: load, apply the domain step, save, map. `OrderDiscountService` hides ownership, policy evaluation and same-coupon retry; `Order` hides amount boundaries and the immutable snapshot. The HTTP entry point captures request-start time once. The public aggregate, persistence reload and HTTP tests cover `INV-001..006` without mocking owned code.

Focused and full module gates require JDK 21, Gradle 8.13 and, on Docker Engine 29, `JAVA_TOOL_OPTIONS=-Dapi.version=1.40`. The confirmed 10,000/1,000/9,000 snapshot survives an EntityManager clear; identical retry responses prove non-accumulation.
