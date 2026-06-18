package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponModelTest {

    private CouponModel coupon(CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        return CouponModel.of("테스트 쿠폰", DiscountPolicy.of(type, value), minOrderAmount, expiredAt, null);
    }

    private LocalDateTime future() {
        return LocalDateTime.of(2999, 12, 31, 23, 59, 59);
    }

    @DisplayName("할인 금액을 계산할 때")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액(FIXED) 쿠폰은 고정 금액만큼 할인한다.")
        @Test
        void fixedDiscount() {
            CouponModel coupon = coupon(CouponType.FIXED, 3000L, null, future());

            long discount = coupon.calculateDiscount(10000L);

            assertThat(discount).isEqualTo(3000L);
        }

        @DisplayName("정액(FIXED) 쿠폰의 할인액이 주문 금액보다 크면, 주문 금액만큼만 할인한다.")
        @Test
        void fixedDiscount_cappedByOrderAmount() {
            CouponModel coupon = coupon(CouponType.FIXED, 30000L, null, future());

            long discount = coupon.calculateDiscount(10000L);

            assertThat(discount).isEqualTo(10000L);
        }

        @DisplayName("정률(RATE) 쿠폰은 주문 금액의 비율만큼 할인한다.")
        @Test
        void rateDiscount() {
            CouponModel coupon = coupon(CouponType.RATE, 10L, null, future());

            long discount = coupon.calculateDiscount(10000L);

            assertThat(discount).isEqualTo(1000L);
        }

        @DisplayName("최소 주문 금액에 미달하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBelowMinOrderAmount() {
            CouponModel coupon = coupon(CouponType.FIXED, 3000L, 10000L, future());

            CoreException result = assertThrows(CoreException.class,
                    () -> coupon.calculateDiscount(9999L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("만료 여부를 확인할 때")
    @Nested
    class IsExpired {

        @DisplayName("현재 시각이 만료 시각 이후면, 만료된 것이다.")
        @Test
        void expired() {
            CouponModel coupon = coupon(CouponType.FIXED, 1000L, null, LocalDateTime.of(2020, 1, 1, 0, 0));

            assertThat(coupon.isExpired(LocalDateTime.of(2020, 1, 2, 0, 0))).isTrue();
        }

        @DisplayName("현재 시각이 만료 시각 이전이면, 만료되지 않은 것이다.")
        @Test
        void notExpired() {
            CouponModel coupon = coupon(CouponType.FIXED, 1000L, null, future());

            assertThat(coupon.isExpired(LocalDateTime.now())).isFalse();
        }
    }

    @DisplayName("쿠폰 수량을 차감할 때")
    @Nested
    class DecreaseQuantity {

        @DisplayName("수량이 남아 있으면, 1만큼 차감된다.")
        @Test
        void decreases() {
            CouponModel coupon = CouponModel.of("한정", DiscountPolicy.of(CouponType.FIXED, 1000L), null, future(), 3);

            coupon.decreaseQuantity();

            assertThat(coupon.getQuantity()).isEqualTo(2);
        }

        @DisplayName("수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenSoldOut() {
            CouponModel coupon = CouponModel.of("한정", DiscountPolicy.of(CouponType.FIXED, 1000L), null, future(), 0);

            CoreException result = assertThrows(CoreException.class, coupon::decreaseQuantity);

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 null(무제한)이면, 차감하지 않는다.")
        @Test
        void skips_whenUnlimited() {
            CouponModel coupon = CouponModel.of("무제한", DiscountPolicy.of(CouponType.FIXED, 1000L), null, future(), null);

            coupon.decreaseQuantity();

            assertThat(coupon.getQuantity()).isNull();
        }
    }

    @DisplayName("DiscountPolicy 생성 시")
    @Nested
    class DiscountPolicyValidation {

        @DisplayName("할인 값이 0 이하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNonPositiveValue() {
            CoreException result = assertThrows(CoreException.class,
                    () -> DiscountPolicy.of(CouponType.FIXED, 0L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률(RATE) 할인이 100을 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRateOver100() {
            CoreException result = assertThrows(CoreException.class,
                    () -> DiscountPolicy.of(CouponType.RATE, 101L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}