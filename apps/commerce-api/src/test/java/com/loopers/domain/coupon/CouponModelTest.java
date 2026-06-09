package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class CouponModelTest {

    @DisplayName("쿠폰 템플릿을 생성할 때,")
    @Nested
    class Create {

        private final ZonedDateTime futureExpiredAt = ZonedDateTime.now().plusDays(7);

        @DisplayName("유효한 값이면 입력값을 그대로 보존한 쿠폰 템플릿이 생성된다.")
        @Test
        void createsCoupon_whenValuesAreValid() {
            // arrange & act
            CouponModel coupon = CouponModel.builder()
                .rawName("신규 가입 쿠폰")
                .type(DiscountType.FIXED)
                .rawValue(5_000)
                .rawMinOrderAmount(10_000)
                .rawExpiredAt(futureExpiredAt)
                .build();

            // assert
            assertAll(
                () -> assertThat(coupon.getName().value()).isEqualTo("신규 가입 쿠폰"),
                () -> assertThat(coupon.getType()).isEqualTo(DiscountType.FIXED),
                () -> assertThat(coupon.getDiscountValue()).isEqualTo(5_000),
                () -> assertThat(coupon.getMinOrderAmount().value()).isEqualTo(10_000),
                () -> assertThat(coupon.getExpiredAt().value()).isEqualTo(futureExpiredAt)
            );
        }

        @DisplayName("최소 주문 금액을 지정하지 않으면 최소 주문 금액 없이 생성된다.")
        @Test
        void createsCoupon_whenMinOrderAmountIsAbsent() {
            // arrange & act
            CouponModel coupon = CouponModel.builder()
                .rawName("정률 쿠폰")
                .type(DiscountType.RATE)
                .rawValue(10)
                .rawMinOrderAmount(null)
                .rawExpiredAt(futureExpiredAt)
                .build();

            // assert
            assertThat(coupon.getMinOrderAmount()).isNull();
        }

        @DisplayName("정률 쿠폰의 할인 값이 100을 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRateValueExceedsMax() {
            // arrange & act & assert
            assertThatThrownBy(() -> CouponModel.builder()
                .rawName("정률 쿠폰")
                .type(DiscountType.RATE)
                .rawValue(101)
                .rawExpiredAt(futureExpiredAt)
                .build())
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료 시각이 현재 시각 이전이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiredAtIsPast() {
            // arrange
            ZonedDateTime pastExpiredAt = ZonedDateTime.now().minusDays(1);

            // act & assert
            assertThatThrownBy(() -> CouponModel.builder()
                .rawName("만료된 쿠폰")
                .type(DiscountType.FIXED)
                .rawValue(5_000)
                .rawExpiredAt(pastExpiredAt)
                .build())
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
