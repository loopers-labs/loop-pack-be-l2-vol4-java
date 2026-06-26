package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTest {

    @DisplayName("결제 모델을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("결제 요청 정보가 유효하면 PENDING 상태로 생성한다.")
        @Test
        void createsPendingPayment_whenRequestIsValid() {
            // act
            Payment payment = new Payment("user1234", 1L, PaymentCardType.SAMSUNG, "1234-5678-9814-1451", 5_000L);

            // assert
            assertAll(
                () -> assertThat(payment.getUserLoginId()).isEqualTo("user1234"),
                () -> assertThat(payment.getOrderId()).isEqualTo(1L),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getPendingReason()).isNull(),
                () -> assertThat(payment.getTransactionKey()).isNull()
            );
        }

        @DisplayName("결제 금액이 0 이하이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenAmountIsNotPositive() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Payment("user1234", 1L, PaymentCardType.SAMSUNG, "1234-5678-9814-1451", 0L);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("PG 처리 결과를 반영할 때, ")
    @Nested
    class ApplyGatewayResult {

        @DisplayName("성공 결과이면 PAID 상태로 전이한다.")
        @Test
        void marksPaid_whenGatewayResultIsSuccess() {
            // arrange
            Payment payment = new Payment("user1234", 1L, PaymentCardType.SAMSUNG, "1234-5678-9814-1451", 5_000L);

            // act
            payment.applyGatewayResult(PaymentGatewayResult.success("20260625:TR:success", "정상 승인되었습니다."));

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID),
                () -> assertThat(payment.getPendingReason()).isNull(),
                () -> assertThat(payment.getTransactionKey()).isEqualTo("20260625:TR:success"),
                () -> assertThat(payment.getReason()).isEqualTo("정상 승인되었습니다.")
            );
        }

        @DisplayName("결과를 알 수 없으면 PENDING 상태를 유지한다.")
        @Test
        void keepsPending_whenGatewayResultIsPending() {
            // arrange
            Payment payment = new Payment("user1234", 1L, PaymentCardType.SAMSUNG, "1234-5678-9814-1451", 5_000L);

            // act
            payment.applyGatewayResult(PaymentGatewayResult.pending(
                null,
                PaymentPendingReason.PG_REQUEST_FAILED,
                "PG 요청 결과를 확인하지 못했습니다."
            ));

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getPendingReason()).isEqualTo(PaymentPendingReason.PG_REQUEST_FAILED),
                () -> assertThat(payment.getTransactionKey()).isNull(),
                () -> assertThat(payment.getReason()).isEqualTo("PG 요청 결과를 확인하지 못했습니다.")
            );
        }
    }

    @DisplayName("PG 빈 조회 결과를 마감할 때, ")
    @Nested
    class FailLookupEmpty {

        @DisplayName("PG_LOOKUP_EMPTY 상태가 유예시간을 넘겼으면 FAILED 상태로 전이한다.")
        @Test
        void marksFailed_whenLookupEmptyGracePeriodHasElapsed() {
            // arrange
            ZonedDateTime createdAt = ZonedDateTime.parse("2026-06-26T13:00:00+09:00[Asia/Seoul]");
            Payment payment = Payment.reconstruct(
                1L,
                "user1234",
                1L,
                PaymentCardType.SAMSUNG,
                "1234-5678-9814-1451",
                5_000L,
                PaymentStatus.PENDING,
                PaymentPendingReason.PG_LOOKUP_EMPTY,
                null,
                "PG에 해당 주문 결제가 없습니다.",
                createdAt
            );

            // act
            payment.failIfLookupEmptyGracePeriodElapsed(
                createdAt.plusMinutes(11),
                Duration.ofMinutes(10)
            );

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getPendingReason()).isNull(),
                () -> assertThat(payment.getReason()).isEqualTo("PG 거래가 확인되지 않아 결제를 실패 처리했습니다.")
            );
        }

        @DisplayName("PG_LOOKUP_EMPTY 상태라도 유예시간이 남아 있으면 PENDING 상태를 유지한다.")
        @Test
        void keepsPending_whenLookupEmptyGracePeriodRemains() {
            // arrange
            ZonedDateTime createdAt = ZonedDateTime.parse("2026-06-26T13:00:00+09:00[Asia/Seoul]");
            Payment payment = Payment.reconstruct(
                1L,
                "user1234",
                1L,
                PaymentCardType.SAMSUNG,
                "1234-5678-9814-1451",
                5_000L,
                PaymentStatus.PENDING,
                PaymentPendingReason.PG_LOOKUP_EMPTY,
                null,
                "PG에 해당 주문 결제가 없습니다.",
                createdAt
            );

            // act
            payment.failIfLookupEmptyGracePeriodElapsed(
                createdAt.plusMinutes(9),
                Duration.ofMinutes(10)
            );

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getPendingReason()).isEqualTo(PaymentPendingReason.PG_LOOKUP_EMPTY),
                () -> assertThat(payment.getReason()).isEqualTo("PG에 해당 주문 결제가 없습니다.")
            );
        }
    }
}
