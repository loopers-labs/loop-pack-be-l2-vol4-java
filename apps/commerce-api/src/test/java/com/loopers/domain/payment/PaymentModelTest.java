package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("domain")
class PaymentModelTest {

    private static PaymentModel pending() {
        return PaymentModel.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);
    }

    @Nested
    @DisplayName("생성 시")
    class Create {

        @Test
        @DisplayName("PENDING 상태이고 거래키가 없어 PG 요청이 필요하다")
        void createsPendingNeedingPgRequest() {
            PaymentModel payment = pending();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getTransactionKey()).isNull();
            assertThat(payment.needsPgRequest()).isTrue();
        }

        @Test
        @DisplayName("결제 금액이 0 이하면 예외가 발생한다")
        void rejectsNonPositiveAmount() {
            assertThatThrownBy(() -> PaymentModel.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9814-1451", 0L))
                .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("거래키 부여 시")
    class AttachTransactionKey {

        @Test
        @DisplayName("거래키가 부여되면 더 이상 PG 요청이 필요하지 않지만 상태는 PENDING 이다")
        void attachesKeyButKeepsPending() {
            PaymentModel payment = pending();

            payment.attachTransactionKey("TR:abc");

            assertThat(payment.getTransactionKey()).isEqualTo("TR:abc");
            assertThat(payment.needsPgRequest()).isFalse();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("빈 거래키는 거부한다")
        void rejectsBlankKey() {
            PaymentModel payment = pending();

            assertThatThrownBy(() -> payment.attachTransactionKey(" "))
                .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("멱등성 판단(isReusable)")
    class Reusable {

        @Test
        @DisplayName("PENDING/SUCCESS 는 재사용 가능, FAILED 는 불가능하다")
        void reusability() {
            PaymentModel pending = pending();

            PaymentModel success = pending();
            success.markSuccess();

            PaymentModel failed = pending();
            failed.markFailed("한도 초과");

            assertThat(pending.isReusable()).isTrue();
            assertThat(success.isReusable()).isTrue();
            assertThat(failed.isReusable()).isFalse();
        }
    }
}
