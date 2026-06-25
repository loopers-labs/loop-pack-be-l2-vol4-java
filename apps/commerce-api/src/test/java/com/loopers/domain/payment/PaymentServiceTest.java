package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private static final String USER_NUMBER = "user1";
    private static final Long ORDER_ID = 1L;
    private static final String ORDER_NUMBER = "order1";
    private static final CardType CARD_TYPE = CardType.SAMSUNG;
    private static final String CARD_NO = "1234-5678-1234-5678";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(1000);

    private PaymentRepository paymentRepository;
    private PaymentGateway paymentGateway;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        paymentGateway = mock(PaymentGateway.class);
        paymentService = new PaymentService(paymentRepository, paymentGateway);
        when(paymentRepository.save(any(PaymentModel.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PaymentGatewayRequest expectedRequest() {
        return new PaymentGatewayRequest(USER_NUMBER, ORDER_NUMBER, CARD_TYPE, CARD_NO, AMOUNT);
    }

    private PaymentModel pay() {
        return paymentService.pay(USER_NUMBER, ORDER_ID, ORDER_NUMBER, CARD_TYPE, CARD_NO, AMOUNT);
    }

    @DisplayName("결제를 요청할 때, ")
    @Nested
    class Pay {

        @DisplayName("PG가 SUCCESS를 응답하면, 결제 상태가 PAID로 저장된다.")
        @Test
        void marksPaymentAsPaid_whenGatewayRespondsSuccess() {
            // given
            when(paymentGateway.requestPayment(expectedRequest())).thenReturn(new PaymentGatewayResponse("txn-1", TransactionStatus.SUCCESS));

            // when
            PaymentModel result = pay();

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(result.getTransactionKey()).isEqualTo("txn-1");
        }

        @DisplayName("PG가 FAILED를 응답하면, 결제 상태가 FAILED로 저장된다.")
        @Test
        void marksPaymentAsFailed_whenGatewayRespondsFailed() {
            // given
            when(paymentGateway.requestPayment(expectedRequest())).thenReturn(new PaymentGatewayResponse("txn-2", TransactionStatus.FAILED));

            // when
            PaymentModel result = pay();

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @DisplayName("PG가 PENDING을 응답하면, 결제 상태는 PENDING으로 유지된다.")
        @Test
        void keepsPaymentPending_whenGatewayRespondsPending() {
            // given
            when(paymentGateway.requestPayment(expectedRequest())).thenReturn(new PaymentGatewayResponse("txn-3", TransactionStatus.PENDING));

            // when
            PaymentModel result = pay();

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("PG 재시도가 모두 실패(RETRY_FAILED)하면, 예외 없이 결제 상태가 UNKNOWN으로 저장된다.")
        @Test
        void marksPaymentAsUnknown_whenGatewayRetryFails() {
            // given
            when(paymentGateway.requestPayment(expectedRequest())).thenThrow(
                    new PaymentGatewayException(FailureReason.RETRY_FAILED, "PG 결제 요청이 재시도 후에도 실패했습니다.")
            );

            // when
            PaymentModel result = pay();

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        }

        @DisplayName("PG 결제 상태 조회 자체가 실패(UNKNOWN)하면, 예외 없이 결제 상태가 UNKNOWN으로 저장된다.")
        @Test
        void marksPaymentAsUnknown_whenGatewayQueryFails() {
            // given
            when(paymentGateway.requestPayment(expectedRequest())).thenThrow(
                    new PaymentGatewayException(FailureReason.UNKNOWN, "PG 결제 상태를 확인할 수 없어 재시도를 중단했습니다.")
            );

            // when
            PaymentModel result = pay();

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        }

        @DisplayName("PG 응답 디코딩에 실패(DECODE_FAILED)하면, CoreException이 발생한다.")
        @Test
        void throwsCoreException_whenGatewayDecodeFails() {
            // given
            when(paymentGateway.requestPayment(expectedRequest())).thenThrow(
                    new PaymentGatewayException(FailureReason.DECODE_FAILED, "PG 결제 요청 응답 디코딩에 실패했습니다.")
            );

            // when
            CoreException result = assertThrows(CoreException.class, PaymentServiceTest.this::pay);

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        }
    }
}
