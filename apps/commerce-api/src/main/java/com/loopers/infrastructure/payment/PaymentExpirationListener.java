package com.loopers.infrastructure.payment;

import com.loopers.application.payment.PaymentFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("!test")
@Component
public class PaymentExpirationListener extends KeyExpirationEventMessageListener {

    private final PaymentFacade paymentFacade;

    public PaymentExpirationListener(RedisMessageListenerContainer listenerContainer, PaymentFacade paymentFacade) {
        super(listenerContainer);
        this.paymentFacade = paymentFacade;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        log.info("Received Redis Key Expired Event: {}", expiredKey);

        if (expiredKey.startsWith("payment_retry:")) {
            try {
                String paymentIdStr = expiredKey.substring("payment_retry:".length());
                Long paymentId = Long.parseLong(paymentIdStr);
                
                paymentFacade.retryOrCompensatePayment(paymentId);
            } catch (Exception e) {
                log.error("Failed to handle payment expiration event for key: {}", expiredKey, e);
            }
        }
    }
}
