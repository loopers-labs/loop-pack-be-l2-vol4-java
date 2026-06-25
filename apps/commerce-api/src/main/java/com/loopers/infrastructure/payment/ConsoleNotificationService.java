package com.loopers.infrastructure.payment;

import com.loopers.application.payment.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConsoleNotificationService implements NotificationService {
    @Override
    public void sendPaymentTimeout(Long userId, Long paymentId) {
        log.info("Sending payment timeout notification to user {}: payment {} canceled", userId, paymentId);
    }
}
