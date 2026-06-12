package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponModelTest {

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    @DisplayName("쿠폰을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsCoupon_whenValid() {
            CouponModel coupon = new CouponModel("신규가입 10% 할인", CouponType.RATE, 10L, 10_000L, FUTURE);

            assertAll(
                () -> assertThat(coupon.getName()).isEqualTo("신규가입 10% 할인"),
                () -> assertThat(coupon.getDiscountType()).isEqualTo(CouponType.RATE),
                () -> assertThat(coupon.getDiscountValue()).isEqualTo(10L),
                () -> assertThat(coupon.getMinOrderAmount()).isEqualTo(10_000L),
                () -> assertThat(coupon.getExpiredAt()).isEqualTo(FUTURE)
            );
        }

        @DisplayName("minOrderAmount가 null이면, 최소 주문 금액 제한 없이 생성된다.")
        @Test
        void createsCoupon_whenMinOrderAmountIsNull() {
            CouponModel coupon = new CouponModel("5천원 할인", CouponType.FIXED, 5_000L, null, FUTURE);

            assertThat(coupon.getMinOrderAmount()).isNull();
        }

        @DisplayName("expiredAt이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiredAtIsNull() {
            CoreException result = assertThrows(CoreException.class, () ->
                new CouponModel("5천원 할인", CouponType.FIXED, 5_000L, null, null)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("minOrderAmount가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMinOrderAmountIsNegative() {
            CoreException result = assertThrows(CoreException.class, () ->
                new CouponModel("5천원 할인", CouponType.FIXED, 5_000L, -1L, FUTURE)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("할인 금액을 계산할 때, ")
    @Nested
    class CalculateDiscount {

        @DisplayName("FIXED 쿠폰이면, 할인 값 그대로 반환된다.")
        @Test
        void returnsFixedValue_whenFixedType() {
            CouponModel coupon = new CouponModel("5천원 할인", CouponType.FIXED, 5_000L, null, FUTURE);

            long discount = coupon.calculateDiscount(100_000L);

            assertThat(discount).isEqualTo(5_000L);
        }

        @DisplayName("RATE 쿠폰이면, 주문 금액의 비율만큼 반환된다.")
        @Test
        void returnsRateValue_whenRateType() {
            CouponModel coupon = new CouponModel("10% 할인", CouponType.RATE, 10L, null, FUTURE);

            long discount = coupon.calculateDiscount(200_000L);

            assertThat(discount).isEqualTo(20_000L);
        }

        @DisplayName("만료된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpired() {
            CouponModel coupon = new CouponModel("5천원 할인", CouponType.FIXED, 5_000L, null, PAST);

            CoreException result = assertThrows(CoreException.class, () ->
                coupon.calculateDiscount(100_000L)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 금액이 최소 주문 금액 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderAmountIsBelowMinimum() {
            CouponModel coupon = new CouponModel("5천원 할인", CouponType.FIXED, 5_000L, 10_000L, FUTURE);

            CoreException result = assertThrows(CoreException.class, () ->
                coupon.calculateDiscount(9_999L)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 금액이 최소 주문 금액과 같으면, 할인이 적용된다.")
        @Test
        void appliesDiscount_whenOrderAmountEqualsMinimum() {
            CouponModel coupon = new CouponModel("5천원 할인", CouponType.FIXED, 5_000L, 10_000L, FUTURE);

            long discount = coupon.calculateDiscount(10_000L);

            assertThat(discount).isEqualTo(5_000L);
        }
    }
}
