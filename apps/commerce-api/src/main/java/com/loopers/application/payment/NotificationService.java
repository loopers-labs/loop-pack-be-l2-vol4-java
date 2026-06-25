package com.loopers.application.payment;

public interface NotificationService {
    void sendPaymentTimeout(Long userId, Long paymentId);
}
