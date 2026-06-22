package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentModelTest {

    private PaymentModel pending() {
        return new PaymentModel(1L, 10L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);
    }

    @Nested
    @DisplayName("결제 생성 시")
    class Create {
        @Test
        @DisplayName("초기 상태는 PENDING이고 카드번호는 마스킹되어 저장된다")
        void given_validInput_when_create_then_pendingAndMasked() {
            PaymentModel payment = pending();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getCardNo()).isEqualTo("1234-****-****-1451");
            assertThat(payment.getId()).isNull();
            assertThat(payment.getTransactionKey()).isNull();
        }

        @Test
        @DisplayName("금액이 0 이하이면 BAD_REQUEST")
        void given_nonPositiveAmount_when_create_then_badRequest() {
            assertThatThrownBy(() -> new PaymentModel(1L, 10L, CardType.KB, "1234-5678-9814-1451", 0L))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("카드번호가 비어 있으면 BAD_REQUEST")
        void given_blankCardNo_when_create_then_badRequest() {
            assertThatThrownBy(() -> new PaymentModel(1L, 10L, CardType.HYUNDAI, "  ", 5000L))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("거래 키 부여 시")
    class AssignKey {
        @Test
        @DisplayName("PG 거래 키를 저장한다")
        void given_key_when_assign_then_stored() {
            PaymentModel payment = pending();
            payment.assignTransactionKey("20260622:TR:abc123");
            assertThat(payment.getTransactionKey()).isEqualTo("20260622:TR:abc123");
        }
    }

    @Nested
    @DisplayName("결제 결과 반영 시")
    class MarkResult {
        @Test
        @DisplayName("PENDING에서 markSuccess하면 SUCCESS가 된다")
        void given_pending_when_markSuccess_then_success() {
            PaymentModel payment = pending();
            payment.markSuccess();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getReason()).isNull();
        }

        @Test
        @DisplayName("PENDING에서 markFailed하면 FAILED + 사유가 기록된다")
        void given_pending_when_markFailed_then_failedWithReason() {
            PaymentModel payment = pending();
            payment.markFailed("한도초과");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getReason()).isEqualTo("한도초과");
        }

        @Test
        @DisplayName("이미 확정된 결제를 다시 확정하면 CONFLICT (멱등 가드)")
        void given_alreadySuccess_when_markAgain_then_conflict() {
            PaymentModel payment = pending();
            payment.markSuccess();

            assertThatThrownBy(payment::markSuccess)
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType").isEqualTo(ErrorType.CONFLICT);
            assertThatThrownBy(() -> payment.markFailed("x"))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType").isEqualTo(ErrorType.CONFLICT);
        }
    }
}
