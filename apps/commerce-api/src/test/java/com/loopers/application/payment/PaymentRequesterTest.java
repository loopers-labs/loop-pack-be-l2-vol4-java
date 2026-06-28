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

/**
 * 서킷 OPEN/예외 시의 fallback 강등은 어노테이션 proxy 가 필요하므로 통합테스트(PaymentResilienceIntegrationTest)에서 검증한다.
 * 여기서는 정상 접수 경로의 조율(개시 금액·카드 정보가 PG 커맨드로 흐르고, 접수 키가 결제에 반영되는지)만 단위로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentRequesterTest {

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentRequester paymentRequester;

    @DisplayName("PG 에 접수 요청하고 키를 반영할 때, ")
    @Nested
    class RequestAndAssign {

        @DisplayName("개시 금액과 카드 정보를 PG 커맨드로 넘기고, 접수 키를 결제에 반영해 PaymentInfo 로 반환한다.")
        @Test
        void sendsCommandAndAssignsTransactionKey() {
            // given
            Long userId = 1L;
            PaymentCommand.Pay command = new PaymentCommand.Pay(100L, "SAMSUNG", "1234-5678-9814-1451");
            PaymentInitiator.Initiated initiated = new PaymentInitiator.Initiated(10L, 50_000L);
            given(paymentGateway.requestPayment(any(PaymentGatewayCommand.class)))
                .willReturn(new PaymentGatewayResult("20260624:TR:abc123", PaymentStatus.PENDING));
            given(paymentService.assignTransactionKey(10L, "20260624:TR:abc123"))
                .willReturn(pendingPaymentWith("20260624:TR:abc123"));

            // when
            PaymentInfo info = paymentRequester.requestAndAssign(userId, command, initiated);

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
