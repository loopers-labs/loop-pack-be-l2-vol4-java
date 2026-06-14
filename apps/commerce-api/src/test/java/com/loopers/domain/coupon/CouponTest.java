package com.loopers.domain.coupon;

import com.loopers.domain.money.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTest {

    private static final LocalDateTime EXPIRED_AT = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    @DisplayName("쿠폰 템플릿을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("유효한 값이면, 정상적으로 생성된다.")
        @Test
        void createsCoupon() {
            // act
            Coupon coupon = new Coupon("신규 10% 할인", new Discount(CouponType.RATE, 10L),
                new Money(BigDecimal.valueOf(10000)), EXPIRED_AT);

            // assert
            assertThat(coupon.getName()).isEqualTo("신규 10% 할인");
            assertThat(coupon.getDiscount().getType()).isEqualTo(CouponType.RATE);
            assertThat(coupon.getMinOrderAmount().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
            assertThat(coupon.getExpiredAt()).isEqualTo(EXPIRED_AT);
        }

        @DisplayName("쿠폰명이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Coupon(" ", new Discount(CouponType.FIXED, 1000L), Money.ZERO, EXPIRED_AT));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료일이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiredAtIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Coupon("쿠폰", new Discount(CouponType.FIXED, 1000L), Money.ZERO, null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 금액의 할인 금액을 계산할 때, ")
    @Nested
    class DiscountFor {
        @DisplayName("최소 주문 금액 이상이면, 할인 정책에 따른 할인 금액을 반환한다.")
        @Test
        void returnsDiscountAmount_whenOrderAmountMeetsMinimum() {
            // arrange
            Coupon coupon = new Coupon("신규 10% 할인", new Discount(CouponType.RATE, 10L),
                new Money(BigDecimal.valueOf(10000)), EXPIRED_AT);

            // act
            Money discountAmount = coupon.discountFor(new Money(BigDecimal.valueOf(20000)));

            // assert
            assertThat(discountAmount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        }

        @DisplayName("최소 주문 금액 미달이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderAmountIsBelowMinimum() {
            // arrange
            Coupon coupon = new Coupon("신규 10% 할인", new Discount(CouponType.RATE, 10L),
                new Money(BigDecimal.valueOf(10000)), EXPIRED_AT);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                coupon.discountFor(new Money(BigDecimal.valueOf(9999))));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최소 주문 금액 조건이 없으면, 검증 없이 할인 금액을 반환한다.")
        @Test
        void returnsDiscountAmount_whenMinOrderAmountIsAbsent() {
            // arrange
            Coupon coupon = new Coupon("천원 할인", new Discount(CouponType.FIXED, 1000L),
                null, EXPIRED_AT);

            // act
            Money discountAmount = coupon.discountFor(new Money(BigDecimal.valueOf(5000)));

            // assert
            assertThat(discountAmount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        }
    }

    @DisplayName("쿠폰 템플릿을 수정할 때, ")
    @Nested
    class Update {
        @DisplayName("주어진 값으로 필드가 변경된다.")
        @Test
        void updatesFields() {
            // arrange
            Coupon coupon = new Coupon("기존", new Discount(CouponType.FIXED, 1000L), Money.ZERO, EXPIRED_AT);
            LocalDateTime newExpiredAt = LocalDateTime.of(2098, 1, 1, 0, 0, 0);

            // act
            coupon.update("변경됨", new Discount(CouponType.RATE, 20L),
                new Money(BigDecimal.valueOf(5000)), newExpiredAt);

            // assert
            assertThat(coupon.getName()).isEqualTo("변경됨");
            assertThat(coupon.getDiscount().getType()).isEqualTo(CouponType.RATE);
            assertThat(coupon.getDiscount().getValue()).isEqualTo(20L);
            assertThat(coupon.getMinOrderAmount().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
            assertThat(coupon.getExpiredAt()).isEqualTo(newExpiredAt);
        }
    }
}
