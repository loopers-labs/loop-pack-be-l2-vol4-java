package com.loopers.support.payment;

import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentWaitingRegistry {

    private final ConcurrentHashMap<String, CompletableFuture<Object>> registry = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> void register(String transactionKey, CompletableFuture<T> future) {
        registry.put(transactionKey, (CompletableFuture<Object>) future);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<CompletableFuture<T>> pop(String transactionKey) {
        return Optional.ofNullable((CompletableFuture<T>) registry.remove(transactionKey));
    }
}
