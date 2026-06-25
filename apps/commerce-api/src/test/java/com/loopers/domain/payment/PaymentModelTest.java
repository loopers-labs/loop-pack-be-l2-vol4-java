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

    @DisplayName("거래 식별자 일치를 검증할 때,")
    @Nested
    class MatchesTransactionKey {

        @DisplayName("기록된 거래 식별자와 같으면 참이다.")
        @Test
        void returnsTrue_whenSame() {
            // arrange
            PaymentModel payment = payment(ZonedDateTime.now());
            payment.recordTransactionKey("TX-0001");

            // act & assert
            assertThat(payment.matchesTransactionKey("TX-0001")).isTrue();
        }

        @DisplayName("다른 거래 식별자이면 거짓이다.")
        @Test
        void returnsFalse_whenDifferent() {
            // arrange
            PaymentModel payment = payment(ZonedDateTime.now());
            payment.recordTransactionKey("TX-0001");

            // act & assert
            assertThat(payment.matchesTransactionKey("TX-FORGED")).isFalse();
        }

        @DisplayName("거래 식별자가 아직 없으면 어떤 값과도 일치하지 않는다.")
        @Test
        void returnsFalse_whenKeyAbsent() {
            // arrange
            PaymentModel payment = payment(ZonedDateTime.now());

            // act & assert
            assertThat(payment.matchesTransactionKey("TX-0001")).isFalse();
        }
    }

    @DisplayName("결제를 확정할 때,")
    @Nested
    class Confirm {

        @DisplayName("PENDING이면 succeed로 SUCCESS로 전이한다.")
        @Test
        void succeeds_whenPending() {
            // arrange
            PaymentModel payment = payment(ZonedDateTime.now());

            // act
            payment.succeed();

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(payment.isTerminal()).isTrue()
            );
        }

        @DisplayName("PENDING이면 fail로 FAILED로 전이하고 사유를 기록한다.")
        @Test
        void fails_whenPending() {
            // arrange
            PaymentModel payment = payment(ZonedDateTime.now());

            // act
            payment.fail("한도 초과");

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getReason()).isEqualTo("한도 초과"),
                () -> assertThat(payment.isTerminal()).isTrue()
            );
        }

        @DisplayName("이미 SUCCESS면 fail을 호출해도 상태와 사유가 바뀌지 않는다.")
        @Test
        void ignoresFail_whenAlreadySuccess() {
            // arrange
            PaymentModel payment = payment(ZonedDateTime.now());
            payment.succeed();

            // act
            payment.fail("한도 초과");

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(payment.getReason()).isNull()
            );
        }

        @DisplayName("이미 FAILED면 succeed를 호출해도 SUCCESS로 바뀌지 않는다.")
        @Test
        void ignoresSucceed_whenAlreadyFailed() {
            // arrange
            PaymentModel payment = payment(ZonedDateTime.now());
            payment.fail("한도 초과");

            // act
            payment.succeed();

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }
    }
}
