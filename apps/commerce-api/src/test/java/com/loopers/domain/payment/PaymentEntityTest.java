package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class PaymentEntityTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final String CARD_NO = "1234-5678-9814-1451";

    private PaymentEntity validPayment() {
        return new PaymentEntity(ORDER_ID, USER_ID, CardType.SAMSUNG, CARD_NO, 10000L);
    }

    @DisplayName("결제 생성")
    @Nested
    class Create {

        @DisplayName("유효한 값으로 생성하면 status는 PENDING이다.")
        @Test
        void create_withPendingStatus() {
            PaymentEntity payment = validPayment();
            assertEquals(PaymentStatus.PENDING, payment.getStatus());
        }

        @DisplayName("orderId가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenOrderIdIsNull() {
            assertThrows(CoreException.class,
                () -> new PaymentEntity(null, USER_ID, CardType.SAMSUNG, CARD_NO, 10000L));
        }

        @DisplayName("userId가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenUserIdIsNull() {
            assertThrows(CoreException.class,
                () -> new PaymentEntity(ORDER_ID, null, CardType.SAMSUNG, CARD_NO, 10000L));
        }

        @DisplayName("cardType이 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenCardTypeIsNull() {
            assertThrows(CoreException.class,
                () -> new PaymentEntity(ORDER_ID, USER_ID, null, CARD_NO, 10000L));
        }

        @DisplayName("cardNo가 빈 문자열이면 예외가 발생한다.")
        @Test
        void throwsException_whenCardNoIsBlank() {
            assertThrows(CoreException.class,
                () -> new PaymentEntity(ORDER_ID, USER_ID, CardType.SAMSUNG, "", 10000L));
        }

        @DisplayName("amount가 0 이하이면 예외가 발생한다.")
        @Test
        void throwsException_whenAmountIsZeroOrNegative() {
            assertThrows(CoreException.class,
                () -> new PaymentEntity(ORDER_ID, USER_ID, CardType.SAMSUNG, CARD_NO, 0L));
        }
    }

    @DisplayName("approve()")
    @Nested
    class Approve {

        @DisplayName("PENDING 상태에서 approve()하면 SUCCESS가 된다.")
        @Test
        void approve_changesStatusToSuccess() {
            PaymentEntity payment = validPayment();
            payment.approve();
            assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        }

        @DisplayName("이미 SUCCESS이면 approve()를 호출해도 예외 없이 무시된다. (멱등)")
        @Test
        void approve_isIdempotent_whenAlreadySuccess() {
            PaymentEntity payment = validPayment();
            payment.approve();
            assertDoesNotThrow(payment::approve);
            assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        }

        @DisplayName("FAILED 상태에서 approve()하면 예외가 발생한다.")
        @Test
        void approve_throwsException_whenStatusIsFailed() {
            PaymentEntity payment = validPayment();
            payment.fail("한도 초과");
            assertThrows(CoreException.class, payment::approve);
        }
    }

    @DisplayName("fail()")
    @Nested
    class Fail {

        @DisplayName("PENDING 상태에서 fail()하면 FAILED가 된다.")
        @Test
        void fail_changesStatusToFailed() {
            PaymentEntity payment = validPayment();
            payment.fail("한도 초과");
            assertEquals(PaymentStatus.FAILED, payment.getStatus());
        }

        @DisplayName("이미 FAILED이면 fail()을 호출해도 예외 없이 무시된다. (멱등)")
        @Test
        void fail_isIdempotent_whenAlreadyFailed() {
            PaymentEntity payment = validPayment();
            payment.fail("한도 초과");
            assertDoesNotThrow(() -> payment.fail("재실패"));
            assertEquals(PaymentStatus.FAILED, payment.getStatus());
        }

        @DisplayName("SUCCESS 상태에서 fail()하면 예외가 발생한다.")
        @Test
        void fail_throwsException_whenStatusIsSuccess() {
            PaymentEntity payment = validPayment();
            payment.approve();
            assertThrows(CoreException.class, () -> payment.fail("오류"));
        }
    }

    @DisplayName("isOwnedBy()")
    @Nested
    class IsOwnedBy {

        @DisplayName("소유자 userId와 일치하면 true를 반환한다.")
        @Test
        void returnsTrue_whenUserIdMatches() {
            assertTrue(validPayment().isOwnedBy(USER_ID));
        }

        @DisplayName("다른 userId이면 false를 반환한다.")
        @Test
        void returnsFalse_whenUserIdDoesNotMatch() {
            assertFalse(validPayment().isOwnedBy(999L));
        }
    }
}
