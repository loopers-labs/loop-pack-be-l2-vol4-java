package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    @Mock
    private PaymentInitiator paymentInitiator;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentFacade paymentFacade;

    @DisplayName("결제를 개시할 때, ")
    @Nested
    class Pay {

        @DisplayName("TX1 개시 → PG 접수 → TX2 키반영 순으로 조율하고, 개시 금액과 접수 키가 각 단계로 흐른다.")
        @Test
        void orchestratesInitiateGatewayAssign() {
            // given
            Long userId = 1L;
            PaymentCommand.Pay command = new PaymentCommand.Pay(100L, "SAMSUNG", "1234-5678-9814-1451");
            given(paymentInitiator.initiate(userId, 100L))
                .willReturn(new PaymentInitiator.Initiated(10L, 50_000L));
            given(paymentGateway.requestPayment(any(PaymentGatewayCommand.class)))
                .willReturn(new PaymentGatewayResult("20260624:TR:abc123", PaymentStatus.PENDING));
            given(paymentService.assignTransactionKey(10L, "20260624:TR:abc123"))
                .willReturn(pendingPaymentWith("20260624:TR:abc123"));

            // when
            PaymentInfo info = paymentFacade.pay(userId, command);

            // then
            ArgumentCaptor<PaymentGatewayCommand> captor = ArgumentCaptor.forClass(PaymentGatewayCommand.class);
            verify(paymentGateway).requestPayment(captor.capture());
            assertAll(
                () -> assertThat(captor.getValue().userId()).isEqualTo(1L),
                () -> assertThat(captor.getValue().orderId()).isEqualTo(100L),
                () -> assertThat(captor.getValue().cardType()).isEqualTo("SAMSUNG"),
                () -> assertThat(captor.getValue().cardNo()).isEqualTo("1234-5678-9814-1451"),
                () -> assertThat(captor.getValue().amount()).isEqualTo(50_000L)
            );
            verify(paymentService).assignTransactionKey(10L, "20260624:TR:abc123");
            assertAll(
                () -> assertThat(info.paymentId()).isEqualTo(10L),
                () -> assertThat(info.status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(info.transactionKey()).isEqualTo("20260624:TR:abc123")
            );
        }
    }

    private PaymentModel pendingPaymentWith(String transactionKey) {
        PaymentModel payment = PaymentModel.createPending(1L, 100L, 50_000L);
        payment.assignTransactionKey(transactionKey);
        ReflectionTestUtils.setField(payment, "id", 10L);
        return payment;
    }
}
