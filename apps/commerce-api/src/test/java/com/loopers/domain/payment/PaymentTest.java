package com.loopers.domain.payment;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Long ORDER_ID = 1351039135L;
    private static final String CARD_NO = "1234-5678-9814-1451";

    private static Payment pending() {
        return Payment.create(ORDER_ID, CardType.SAMSUNG, CARD_NO, Money.of(5_000L));
    }

    @DisplayName("Payment 를 create 로 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("상태는 PENDING, transactionKey 는 미발급(null), 카드번호는 마스킹되어 보관된다.")
        @Test
        void createsPendingPayment_withMaskedCardNo() {
            Payment payment = pending();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getTransactionKey()).isNull();
            assertThat(payment.getReason()).isNull();
            assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(payment.getCardType()).isEqualTo(CardType.SAMSUNG);
            assertThat(payment.getAmount().getAmount()).isEqualTo(5_000L);
        }

        @DisplayName("풀 카드번호(PAN)는 저장하지 않고 마지막 4자리만 노출하는 마스킹 형태로 보관한다.")
        @Test
        void masksPan_keepingOnlyLast4() {
            Payment payment = pending();

            assertThat(payment.getMaskedCardNo()).isEqualTo("**** **** **** 1451");
            assertThat(payment.getMaskedCardNo()).doesNotContain("1234", "5678", "9814");
        }

        @DisplayName("카드번호 형식이 xxxx-xxxx-xxxx-xxxx 가 아니면 BAD_REQUEST 로 거부한다.")
        @Test
        void rejectsInvalidCardNoFormat() {
            assertThatThrownBy(() -> Payment.create(ORDER_ID, CardType.SAMSUNG, "1234567898141451", Money.of(5_000L)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("필수값(orderId·cardType·amount)이 비면 BAD_REQUEST 로 거부한다.")
        @Test
        void rejectsNullRequiredFields() {
            assertThatThrownBy(() -> Payment.create(null, CardType.SAMSUNG, CARD_NO, Money.of(5_000L)))
                    .isInstanceOf(CoreException.class);
            assertThatThrownBy(() -> Payment.create(ORDER_ID, null, CARD_NO, Money.of(5_000L)))
                    .isInstanceOf(CoreException.class);
            assertThatThrownBy(() -> Payment.create(ORDER_ID, CardType.SAMSUNG, CARD_NO, null))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("PG 접수 후 transactionKey 를 보관할 때, ")
    @Nested
    class AssignTransactionKey {

        @DisplayName("발급받은 키를 보관한다. (상태는 여전히 PENDING)")
        @Test
        void storesKey_keepingPending() {
            Payment payment = pending();

            payment.assignTransactionKey("20250816:TR:9577c5");

            assertThat(payment.getTransactionKey()).isEqualTo("20250816:TR:9577c5");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("빈 키는 BAD_REQUEST 로 거부한다.")
        @Test
        void rejectsBlankKey() {
            Payment payment = pending();

            assertThatThrownBy(() -> payment.assignTransactionKey(" "))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("결과를 확정할 때, ")
    @Nested
    class Transition {

        @DisplayName("PENDING 에서 markSuccess 는 SUCCESS 로 전이하고 true 를 반환한다.")
        @Test
        void pendingToSuccess() {
            Payment payment = pending();

            boolean transitioned = payment.markSuccess();

            assertThat(transitioned).isTrue();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.isSuccess()).isTrue();
        }

        @DisplayName("PENDING 에서 markFailed 는 FAILED 로 전이하고 사유를 보관한다.")
        @Test
        void pendingToFailed() {
            Payment payment = pending();

            boolean transitioned = payment.markFailed("한도초과");

            assertThat(transitioned).isTrue();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getReason()).isEqualTo("한도초과");
        }

        @DisplayName("이미 SUCCESS 인 결제에 다시 markSuccess/markFailed 하면 멱등하게 무시하고 false 를 반환한다. (콜백 중복·정산 동시성 흡수)")
        @Test
        void terminalIsIdempotent() {
            Payment payment = pending();
            payment.markSuccess();

            assertThat(payment.markSuccess()).isFalse();
            assertThat(payment.markFailed("뒤늦은 실패 콜백")).isFalse();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getReason()).isNull();
        }

        @DisplayName("이미 FAILED 인 결제는 markSuccess 로 뒤집히지 않는다.")
        @Test
        void failedDoesNotFlipToSuccess() {
            Payment payment = pending();
            payment.markFailed("잘못된 카드");

            assertThat(payment.markSuccess()).isFalse();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getReason()).isEqualTo("잘못된 카드");
        }
    }
}
