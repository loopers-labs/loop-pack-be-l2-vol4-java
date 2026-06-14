package com.loopers.domain.coupon;

import com.loopers.domain.money.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiscountTest {

    @DisplayName("할인 정책을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("정액(FIXED) 타입과 0보다 큰 값이면, 정상적으로 생성된다.")
        @Test
        void createsFixedDiscount() {
            // act
            Discount discount = new Discount(CouponType.FIXED, 5000L);

            // assert
            assertThat(discount.getType()).isEqualTo(CouponType.FIXED);
            assertThat(discount.getValue()).isEqualTo(5000L);
        }

        @DisplayName("정률(RATE) 타입과 100 이하의 값이면, 정상적으로 생성된다.")
        @Test
        void createsRateDiscount() {
            // act
            Discount discount = new Discount(CouponType.RATE, 10L);

            // assert
            assertThat(discount.getType()).isEqualTo(CouponType.RATE);
            assertThat(discount.getValue()).isEqualTo(10L);
        }

        @DisplayName("타입이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTypeIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Discount(null, 1000L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인값이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsZeroOrNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Discount(CouponType.FIXED, 0L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률(RATE)인데 할인값이 100을 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRateExceeds100() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Discount(CouponType.RATE, 101L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("할인을 적용할 때, ")
    @Nested
    class Apply {
        @DisplayName("정액(FIXED) 쿠폰이면, 할인값 그대로를 할인 금액으로 반환한다.")
        @Test
        void returnsFixedValue_whenTypeIsFixed() {
            // arrange
            Discount discount = new Discount(CouponType.FIXED, 5000L);
            Money orderAmount = new Money(BigDecimal.valueOf(20000));

            // act
            Money discountAmount = discount.apply(orderAmount);

            // assert
            assertThat(discountAmount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        }

        @DisplayName("정률(RATE) 쿠폰이면, 주문 금액의 비율만큼 소수점 이하를 절사한 할인 금액을 반환한다.")
        @Test
        void returnsFlooredRateAmount_whenTypeIsRate() {
            // arrange
            Discount discount = new Discount(CouponType.RATE, 10L);
            Money orderAmount = new Money(BigDecimal.valueOf(9999));

            // act
            Money discountAmount = discount.apply(orderAmount);

            // assert
            assertThat(discountAmount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(999));
        }

        @DisplayName("정액(FIXED) 할인값이 주문 금액보다 크면, 할인 금액은 주문 금액으로 제한된다.")
        @Test
        void capsDiscountAtOrderAmount_whenFixedValueExceedsOrderAmount() {
            // arrange
            Discount discount = new Discount(CouponType.FIXED, 5000L);
            Money orderAmount = new Money(BigDecimal.valueOf(3000));

            // act
            Money discountAmount = discount.apply(orderAmount);

            // assert
            assertThat(discountAmount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }
    }
}
