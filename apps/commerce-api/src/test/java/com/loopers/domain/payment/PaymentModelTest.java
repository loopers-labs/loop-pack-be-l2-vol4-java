package com.loopers.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PaymentModelTest {

    private static final String CARD_NO = "1234-5678-9012-3456";

    private PaymentModel payment(ZonedDateTime requestedAt) {
        return PaymentModel.builder()
            .orderId(1L)
            .userId(2L)
            .amount(78_000)
            .cardType(CardType.SAMSUNG)
            .rawCardNo(CARD_NO)
            .requestedAt(requestedAt)
            .build();
    }

    @DisplayName("결제를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("접수 대기 상태로 시작하고 입력한 결제 정보와 접수 시각을 보존한다.")
        @Test
        void createsPendingPayment_withRequestedAt() {
            // arrange
            ZonedDateTime requestedAt = ZonedDateTime.now();

            // act
            PaymentModel payment = payment(requestedAt);

            // assert
            assertAll(
                () -> assertThat(payment.getOrderId()).isEqualTo(1L),
                () -> assertThat(payment.getUserId()).isEqualTo(2L),
                () -> assertThat(payment.getAmount()).isEqualTo(78_000),
                () -> assertThat(payment.getCardType()).isEqualTo(CardType.SAMSUNG),
                () -> assertThat(payment.getCardNo().value()).isEqualTo(CARD_NO),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.isPending()).isTrue(),
                () -> assertThat(payment.getRequestedAt()).isEqualTo(requestedAt)
            );
        }

        @DisplayName("거래 식별자는 부재 상태로 시작한다.")
        @Test
        void startsWithoutTransactionKey() {
            // act
            PaymentModel payment = payment(ZonedDateTime.now());

            // assert
            assertThat(payment.getTransactionKey()).isNull();
        }
    }

    @DisplayName("거래 식별자를 기록할 때,")
    @Nested
    class RecordTransactionKey {

        @DisplayName("접수 응답의 거래 식별자를 결제에 기록한다.")
        @Test
        void recordsTransactionKey() {
            // arrange
            PaymentModel payment = payment(ZonedDateTime.now());

            // act
            payment.recordTransactionKey("TX-0001");

            // assert
            assertThat(payment.getTransactionKey()).isEqualTo("TX-0001");
        }
    }
}
