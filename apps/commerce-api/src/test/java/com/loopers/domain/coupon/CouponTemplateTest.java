package com.loopers.domain.coupon;

import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.model.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTemplateTest {

    private final ZonedDateTime futureExpiry = ZonedDateTime.now().plusDays(30);

    @DisplayName("쿠폰 템플릿 생성 시, ")
    @Nested
    class Create {

        @DisplayName("정액 쿠폰을 정상 생성한다.")
        @Test
        void createsFixedCoupon() {
            CouponTemplate coupon = CouponTemplate.create("신규가입 1000원 할인", CouponType.FIXED, 1000L, 5000L, futureExpiry);
            assertThat(coupon.getName()).isEqualTo("신규가입 1000원 할인");
            assertThat(coupon.getType()).isEqualTo(CouponType.FIXED);
            assertThat(coupon.getValue()).isEqualTo(1000L);
        }

        @DisplayName("정률 쿠폰을 정상 생성한다.")
        @Test
        void createsRateCoupon() {
            CouponTemplate coupon = CouponTemplate.create("10% 할인", CouponType.RATE, 10L, null, futureExpiry);
            assertThat(coupon.getType()).isEqualTo(CouponType.RATE);
            assertThat(coupon.getValue()).isEqualTo(10L);
        }

        @DisplayName("이름이 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            CoreException ex = assertThrows(CoreException.class, () ->
                CouponTemplate.create("", CouponType.FIXED, 1000L, null, futureExpiry)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("value가 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsZeroOrNegative() {
            CoreException ex = assertThrows(CoreException.class, () ->
                CouponTemplate.create("할인", CouponType.FIXED, 0L, null, futureExpiry)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 쿠폰의 value가 100 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRateValueExceeds100() {
            CoreException ex = assertThrows(CoreException.class, () ->
                CouponTemplate.create("할인", CouponType.RATE, 101L, null, futureExpiry)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료일이 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiredAtIsNull() {
            CoreException ex = assertThrows(CoreException.class, () ->
                CouponTemplate.create("할인", CouponType.FIXED, 1000L, null, null)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("할인 금액 계산 시, ")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액 쿠폰은 value만큼 할인한다.")
        @Test
        void fixedDiscount() {
            CouponTemplate coupon = CouponTemplate.create("1000원 할인", CouponType.FIXED, 1000L, null, futureExpiry);
            assertThat(coupon.calculateDiscount(5000L)).isEqualTo(1000L);
        }

        @DisplayName("정액 할인이 주문 금액보다 크면 주문 금액 전체를 할인한다.")
        @Test
        void fixedDiscount_cappedAtOrderAmount() {
            CouponTemplate coupon = CouponTemplate.create("5000원 할인", CouponType.FIXED, 5000L, null, futureExpiry);
            assertThat(coupon.calculateDiscount(3000L)).isEqualTo(3000L);
        }

        @DisplayName("정률 쿠폰은 비율만큼 할인한다.")
        @Test
        void rateDiscount() {
            CouponTemplate coupon = CouponTemplate.create("10% 할인", CouponType.RATE, 10L, null, futureExpiry);
            assertThat(coupon.calculateDiscount(10000L)).isEqualTo(1000L);
        }

        @DisplayName("최소 주문 금액 미달 시, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBelowMinOrderAmount() {
            CouponTemplate coupon = CouponTemplate.create("1000원 할인", CouponType.FIXED, 1000L, 5000L, futureExpiry);
            CoreException ex = assertThrows(CoreException.class, () ->
                coupon.calculateDiscount(4000L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
