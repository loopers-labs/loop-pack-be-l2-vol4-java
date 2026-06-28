package com.loopers.application.order;

public interface IdempotencyManager {
    Long getSuccess(String idempotencyKey);
    boolean lock(String idempotencyKey);
    void unlock(String idempotencyKey);
    void saveSuccess(String idempotencyKey, Long orderId);
}
