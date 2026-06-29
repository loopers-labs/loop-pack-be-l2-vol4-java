package com.loopers.application.payment;

import java.time.Duration;

public interface PaymentTempStorage {
    void setRetryCount(Long paymentId, int count, Duration ttl);
    void deleteRetryKey(Long paymentId);
    Integer getRetryCount(Long paymentId);

    boolean lockOrder(Long orderId);
    void unlockOrder(Long orderId);
}
