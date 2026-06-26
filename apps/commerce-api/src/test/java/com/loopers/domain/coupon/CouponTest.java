package com.loopers.domain.coupon;

import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTest {

    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    @DisplayName("쿠폰을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정률 쿠폰의 할인율이 100% 를 넘으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRateExceedsHundred() {
            CoreException result = assertThrows(CoreException.class,
                () -> Coupon.create("불량 쿠폰", CouponType.RATE, 150L, null, FAR_FUTURE));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인 값이 0 이하이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNotPositive() {
            CoreException result = assertThrows(CoreException.class,
                () -> Coupon.create("0원 쿠폰", CouponType.FIXED, 0L, null, FAR_FUTURE));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료 시각이 없으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiredAtIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> Coupon.create("쿠폰", CouponType.FIXED, 1000L, null, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("만료 여부를 판단할 때, ")
    @Nested
    class IsExpired {

        @DisplayName("현재 시각이 만료 시각보다 늦으면 true 를 반환한다.")
        @Test
        void returnsTrue_whenNowIsAfterExpiredAt() {
            Coupon coupon = Coupon.create("만료 쿠폰", CouponType.FIXED, 1000L, null,
                LocalDateTime.of(2024, 1, 1, 0, 0));
            assertThat(coupon.isExpired(LocalDateTime.of(2024, 1, 2, 0, 0))).isTrue();
        }

        @DisplayName("현재 시각이 만료 시각 이전이면 false 를 반환한다.")
        @Test
        void returnsFalse_whenNowIsBeforeExpiredAt() {
            Coupon coupon = Coupon.create("유효 쿠폰", CouponType.FIXED, 1000L, null, FAR_FUTURE);
            assertThat(coupon.isExpired(LocalDateTime.now())).isFalse();
        }
    }

    @DisplayName("할인 금액을 계산할 때, ")
    @Nested
    class Discount {

        @DisplayName("정액 쿠폰은 value 만큼 할인된다.")
        @Test
        void fixed_discountsByValue() {
            Coupon coupon = Coupon.create("3천원 할인", CouponType.FIXED, 3_000L, null, FAR_FUTURE);
            assertThat(coupon.discount(Money.of(10_000L))).isEqualTo(Money.of(3_000L));
        }

        @DisplayName("정률 쿠폰은 orderTotal × value/100 만큼 할인된다 (원 단위 버림).")
        @Test
        void rate_discountsByPercentage() {
            Coupon coupon = Coupon.create("10% 할인", CouponType.RATE, 10L, null, FAR_FUTURE);
            assertThat(coupon.discount(Money.of(10_000L))).isEqualTo(Money.of(1_000L));
        }

        @DisplayName("최소 주문 금액에 미달하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBelowMinOrderAmount() {
            Coupon coupon = Coupon.create("3만원 이상 5천원 할인", CouponType.FIXED, 5_000L, 30_000L, FAR_FUTURE);
            CoreException result = assertThrows(CoreException.class,
                () -> coupon.discount(Money.of(20_000L)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인 결과가 주문 총액을 넘으면 주문 총액만큼만 할인한다 (음수 결제 방지).")
        @Test
        void cap_whenDiscountExceedsOrderTotal() {
            Coupon coupon = Coupon.create("초대형 할인", CouponType.FIXED, 100_000L, null, FAR_FUTURE);
            assertThat(coupon.discount(Money.of(5_000L))).isEqualTo(Money.of(5_000L));
        }
    }
}
