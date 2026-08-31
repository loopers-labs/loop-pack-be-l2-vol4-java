package com.loopers.support.incident;

import com.loopers.domain.payment.PaymentGateway;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Hides: deterministic COMMIT_PENDING_THEN_DROP ordinals and second-dispatch rejection.
public class CheckoutLatencyAIncidentFixture implements PaymentGateway {
    private final Map<String, Result> providerTransactions = new LinkedHashMap<>();
    private int dispatches;
    private boolean faultEnabled;

    public void enableFault() {
        faultEnabled = true;
    }

    @Override
    public Result create(String userId, Request request) {
        if (!"135135".equals(userId)) {
            throw new AssertionError("unexpected X-USER-ID");
        }
        if (providerTransactions.containsKey(request.orderId())) {
            throw new AssertionError("REJECT_SECOND_DISPATCH");
        }
        dispatches++;
        Result result = new Result("tx-%02d".formatted(dispatches), request.orderId(), "CONFIRMED");
        providerTransactions.put(request.orderId(), result);
        if (faultEnabled && dispatches >= 4 && dispatches <= 6) {
            throw new RuntimeException("COMMIT_PENDING_THEN_DROP");
        }
        return result;
    }

    @Override
    public List<Result> findByOrderId(String userId, String providerOrderId) {
        Result result = providerTransactions.get(providerOrderId);
        return result == null ? List.of() : List.of(result);
    }

    @Override
    public Result findByTransactionKey(String userId, String key) {
        return providerTransactions.values().stream()
            .filter(result -> result.transactionKey().equals(key))
            .findFirst().orElseThrow();
    }

    public int providerEffects() {
        return providerTransactions.size();
    }

    public int dispatches() {
        return dispatches;
    }

    public void reset() {
        providerTransactions.clear();
        dispatches = 0;
        faultEnabled = false;
    }
}
