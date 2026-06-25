package com.loopers.infrastructure.payment;

import com.loopers.application.payment.PaymentFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.*;

class PaymentExpirationListenerTest {

    @Test
    @DisplayName("만료된 Redis 키가 payment_retry 포맷이면 PaymentFacade.retryOrCompensatePayment가 호출된다.")
    void onMessage_WithPaymentRetryKey_ShouldInvokeFacade() {
        // given
        RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
        PaymentFacade paymentFacade = mock(PaymentFacade.class);
        PaymentExpirationListener listener = new PaymentExpirationListener(container, paymentFacade);

        Message message = mock(Message.class);
        when(message.toString()).thenReturn("payment_retry:123");

        // when
        listener.onMessage(message, new byte[0]);

        // then
        verify(paymentFacade, times(1)).retryOrCompensatePayment(123L);
    }

    @Test
    @DisplayName("만료된 Redis 키가 payment_retry 포맷이 아니면 PaymentFacade.retryOrCompensatePayment가 호출되지 않는다.")
    void onMessage_WithOtherKey_ShouldNotInvokeFacade() {
        // given
        RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
        PaymentFacade paymentFacade = mock(PaymentFacade.class);
        PaymentExpirationListener listener = new PaymentExpirationListener(container, paymentFacade);

        Message message = mock(Message.class);
        when(message.toString()).thenReturn("other_key:123");

        // when
        listener.onMessage(message, new byte[0]);

        // then
        verify(paymentFacade, never()).retryOrCompensatePayment(anyLong());
    }
}
