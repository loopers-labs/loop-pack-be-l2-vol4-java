package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponModelTest {

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    @DisplayName("CouponModel 생성 시,")
    @Nested
    class Create {

        @DisplayName("name이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel(null, CouponType.FIXED, 1000, null, FUTURE));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 공백이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel("  ", CouponType.FIXED, 1000, null, FUTURE));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("type이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTypeIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel("할인쿠폰", null, 1000, null, FUTURE));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("value가 0 이하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsZeroOrNegative() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel("할인쿠폰", CouponType.FIXED, 0, null, FUTURE));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 쿠폰의 value가 100 초과면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRateCouponValueExceeds100() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel("할인쿠폰", CouponType.RATE, 101, null, FUTURE));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("expiredAt이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiredAtIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel("할인쿠폰", CouponType.FIXED, 1000, null, null));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("minOrderAmount가 0이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMinOrderAmountIsZero() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel("할인쿠폰", CouponType.FIXED, 1000, 0, FUTURE));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("minOrderAmount가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMinOrderAmountIsNegative() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel("할인쿠폰", CouponType.FIXED, 1000, -1, FUTURE));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("minOrderAmount가 null이면 최소 주문 금액 없이 정상 생성된다.")
        @Test
        void createsCoupon_whenMinOrderAmountIsNull() {
            CouponModel coupon = new CouponModel("할인쿠폰", CouponType.FIXED, 1000, null, FUTURE);

            assertThat(coupon).isNotNull();
        }

        @DisplayName("minOrderAmount가 양수이면 정상 생성된다.")
        @Test
        void createsCoupon_whenMinOrderAmountIsPositive() {
            CouponModel coupon = new CouponModel("할인쿠폰", CouponType.FIXED, 1000, 5000, FUTURE);

            assertThat(coupon).isNotNull();
        }
    }

    @DisplayName("calculateDiscount()를 호출할 때,")
    @Nested
    class CalculateDiscount {

        @DisplayName("FIXED 쿠폰은 value만큼 할인된다.")
        @Test
        void returnsFixedDiscount() {
            CouponModel coupon = new CouponModel("정액쿠폰", CouponType.FIXED, 3_000, null, FUTURE);

            int discount = coupon.calculateDiscount(10_000);

            assertThat(discount).isEqualTo(3_000);
        }

        @DisplayName("FIXED 쿠폰의 value가 주문 금액보다 크면 주문 금액만큼만 할인된다.")
        @Test
        void capsDiscountAtOrderAmount_whenFixedValueExceedsOrderAmount() {
            CouponModel coupon = new CouponModel("정액쿠폰", CouponType.FIXED, 50_000, null, FUTURE);

            int discount = coupon.calculateDiscount(10_000);

            assertThat(discount).isEqualTo(10_000);
        }

        @DisplayName("RATE 쿠폰은 주문 금액의 비율만큼 할인된다.")
        @Test
        void returnsRateDiscount() {
            CouponModel coupon = new CouponModel("정률쿠폰", CouponType.RATE, 10, null, FUTURE);

            int discount = coupon.calculateDiscount(10_000);

            assertThat(discount).isEqualTo(1_000);
        }

        @DisplayName("주문 금액이 minOrderAmount 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderAmountBelowMinimum() {
            CouponModel coupon = new CouponModel("최소금액쿠폰", CouponType.FIXED, 1_000, 10_000, FUTURE);

            CoreException result = assertThrows(CoreException.class,
                () -> coupon.calculateDiscount(5_000));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("isExpired()를 호출할 때,")
    @Nested
    class IsExpired {

        @DisplayName("만료일이 과거이면 true를 반환한다.")
        @Test
        void returnsTrue_whenExpiredAtIsInPast() {
            CouponModel coupon = new CouponModel("만료쿠폰", CouponType.FIXED, 1_000, null, PAST);

            assertThat(coupon.isExpired()).isTrue();
        }

        @DisplayName("만료일이 미래이면 false를 반환한다.")
        @Test
        void returnsFalse_whenExpiredAtIsInFuture() {
            CouponModel coupon = new CouponModel("유효쿠폰", CouponType.FIXED, 1_000, null, FUTURE);

            assertThat(coupon.isExpired()).isFalse();
        }
    }
}
