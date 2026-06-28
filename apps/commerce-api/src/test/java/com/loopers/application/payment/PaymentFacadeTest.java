package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    @Mock
    private PaymentInitiator paymentInitiator;

    @Mock
    private PaymentRequester paymentRequester;

    @InjectMocks
    private PaymentFacade paymentFacade;

    @DisplayName("결제를 개시할 때, ")
    @Nested
    class Pay {

        @DisplayName("TX1 개시 → (서킷 보호되는) PG 접수·키반영 순으로 조율하고, 개시 결과를 그 단계로 넘긴다.")
        @Test
        void orchestratesInitiateThenRequest() {
            // given
            Long userId = 1L;
            PaymentCommand.Pay command = new PaymentCommand.Pay(100L, "SAMSUNG", "1234-5678-9814-1451");
            PaymentInitiator.Initiated initiated = new PaymentInitiator.Initiated(10L, 50_000L);
            PaymentInfo expected = new PaymentInfo(10L, 100L, PaymentStatus.PENDING, "20260624:TR:abc123");
            given(paymentInitiator.initiate(userId, 100L)).willReturn(initiated);
            given(paymentRequester.requestAndAssign(userId, command, initiated)).willReturn(expected);

            // when
            PaymentInfo info = paymentFacade.pay(userId, command);

            // then
            var ordered = inOrder(paymentInitiator, paymentRequester);
            ordered.verify(paymentInitiator).initiate(userId, 100L);
            ordered.verify(paymentRequester).requestAndAssign(userId, command, initiated);
            assertThat(info).isEqualTo(expected);
        }
    }
}
