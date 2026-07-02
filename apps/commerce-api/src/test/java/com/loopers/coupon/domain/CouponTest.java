package com.loopers.coupon.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTest {

    private static final ZonedDateTime FUTURE =
        ZonedDateTime.of(2999, 12, 31, 23, 59, 59, 0, ZoneId.of("Asia/Seoul"));

    @DisplayName("할인 금액을 계산할 때,")
    @Nested
    class CalculateDiscount {
        @DisplayName("정액(FIXED) 쿠폰은 고정 금액을 할인한다.")
        @Test
        void fixed() {
            Coupon coupon = new Coupon("정액", CouponType.FIXED, 3_000L, null, FUTURE);
            assertThat(coupon.calculateDiscount(10_000L)).isEqualTo(3_000L);
        }

        @DisplayName("정률(RATE) 쿠폰은 퍼센트만큼 할인한다.")
        @Test
        void rate() {
            Coupon coupon = new Coupon("정률", CouponType.RATE, 10L, null, FUTURE);
            assertThat(coupon.calculateDiscount(10_000L)).isEqualTo(1_000L);
        }

        @DisplayName("할인 금액이 주문 금액을 초과하면 주문 금액으로 캡한다.")
        @Test
        void capsAtOrderAmount() {
            Coupon coupon = new Coupon("정액", CouponType.FIXED, 50_000L, null, FUTURE);
            assertThat(coupon.calculateDiscount(10_000L)).isEqualTo(10_000L);
        }

        @DisplayName("최소 주문 금액 미달이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBelowMinOrderAmount() {
            Coupon coupon = new Coupon("정액", CouponType.FIXED, 3_000L, 10_000L, FUTURE);
            CoreException result =
                assertThrows(CoreException.class, () -> coupon.calculateDiscount(9_999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰을 생성할 때,")
    @Nested
    class Create {
        @DisplayName("정률 할인율이 100을 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRateOver100() {
            CoreException result =
                assertThrows(
                    CoreException.class, () -> new Coupon("정률", CouponType.RATE, 101L, null, FUTURE));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정액 할인 금액이 0 이하이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenFixedNotPositive() {
            CoreException result =
                assertThrows(
                    CoreException.class, () -> new Coupon("정액", CouponType.FIXED, 0L, null, FUTURE));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("만료 시각이 지났으면 만료로 판정한다.")
    @Test
    void isExpired() {
        ZonedDateTime past = ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        Coupon coupon = new Coupon("정액", CouponType.FIXED, 1_000L, null, past);
        assertThat(coupon.isExpired(ZonedDateTime.now())).isTrue();
    }
}
