package com.loopers.payment.interfaces.api;

import com.loopers.payment.application.PaymentCallbackCommand;
import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.PaymentFailureReason;
import com.loopers.payment.domain.PgPaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentV1DtoTest {

    private static final String TRANSACTION_KEY = "20250816:TR:9577c5";
    private static final Long ORDER_ID = 1_351_039_135L;
    private static final long AMOUNT = 1_550_000L;

    @DisplayName("PG 콜백 요청을 command 로 바꿀 때")
    @Nested
    class ToCommand {

        @DisplayName("한도 초과 실패 사유를 내부 실패 사유로 변환한다.")
        @Test
        void mapsLimitExceededReason_whenPgReasonContainsLimitText() {
            // arrange
            PaymentV1Dto.PaymentCallbackRequest request = new PaymentV1Dto.PaymentCallbackRequest(
                TRANSACTION_KEY,
                ORDER_ID,
                CardType.SAMSUNG,
                AMOUNT,
                PgPaymentStatus.FAILED,
                "한도초과입니다. 다른 카드를 선택해주세요."
            );

            // act
            PaymentCallbackCommand command = request.toCommand();

            // assert
            assertThat(command.failureReason()).isEqualTo(PaymentFailureReason.LIMIT_EXCEEDED);
        }

        @DisplayName("분류할 수 없는 실패 사유는 PG 거래 실패로 변환한다.")
        @Test
        void mapsGenericTransactionFailure_whenPgReasonIsUnknown() {
            // arrange
            PaymentV1Dto.PaymentCallbackRequest request = new PaymentV1Dto.PaymentCallbackRequest(
                TRANSACTION_KEY,
                ORDER_ID,
                CardType.SAMSUNG,
                AMOUNT,
                PgPaymentStatus.FAILED,
                "PG rejected payment."
            );

            // act
            PaymentCallbackCommand command = request.toCommand();

            // assert
            assertThat(command.failureReason()).isEqualTo(PaymentFailureReason.PG_TRANSACTION_FAILED);
        }

        @DisplayName("성공 콜백에는 내부 실패 사유를 채우지 않는다.")
        @Test
        void mapsNoFailureReason_whenCallbackIsSuccess() {
            // arrange
            PaymentV1Dto.PaymentCallbackRequest request = new PaymentV1Dto.PaymentCallbackRequest(
                TRANSACTION_KEY,
                ORDER_ID,
                CardType.SAMSUNG,
                AMOUNT,
                PgPaymentStatus.SUCCESS,
                "정상 승인되었습니다."
            );

            // act
            PaymentCallbackCommand command = request.toCommand();

            // assert
            assertThat(command.failureReason()).isNull();
        }
    }
}
