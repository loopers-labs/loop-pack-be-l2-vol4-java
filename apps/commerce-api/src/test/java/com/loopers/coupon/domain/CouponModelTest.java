package com.loopers.coupon.domain;

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

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(7);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    @DisplayName("CouponModel을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정상 FIXED 요청이면, CouponModel이 생성된다.")
        @Test
        void createsCouponModel_whenFixedTypeIsValid() {
            // arrange & act
            CouponModel coupon = new CouponModel("신규가입 1000원 할인", CouponType.FIXED, 1000L, null, FUTURE);

            // assert
            assertAll(
                () -> assertThat(coupon.getName()).isEqualTo("신규가입 1000원 할인"),
                () -> assertThat(coupon.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(coupon.getValue()).isEqualTo(1000L)
            );
        }

        @DisplayName("정상 RATE 요청이면, CouponModel이 생성된다.")
        @Test
        void createsCouponModel_whenRateTypeIsValid() {
            // arrange & act
            CouponModel coupon = new CouponModel("10% 할인", CouponType.RATE, 10L, null, FUTURE);

            // assert
            assertAll(
                () -> assertThat(coupon.getType()).isEqualTo(CouponType.RATE),
                () -> assertThat(coupon.getValue()).isEqualTo(10L)
            );
        }

        @DisplayName("name이 null이거나 blank이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act & assert
            assertAll(
                () -> assertThrows(CoreException.class, () -> new CouponModel(null, CouponType.FIXED, 1000L, null, FUTURE)),
                () -> assertThrows(CoreException.class, () -> new CouponModel("  ", CouponType.FIXED, 1000L, null, FUTURE))
            );
        }

        @DisplayName("type이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTypeIsNull() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                new CouponModel("할인쿠폰", null, 1000L, null, FUTURE)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("value가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                new CouponModel("할인쿠폰", CouponType.FIXED, null, null, FUTURE)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("FIXED 타입에서 value가 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenFixedValueIsZeroOrNegative() {
            // act & assert
            assertAll(
                () -> assertThrows(CoreException.class, () -> new CouponModel("할인쿠폰", CouponType.FIXED, 0L, null, FUTURE)),
                () -> assertThrows(CoreException.class, () -> new CouponModel("할인쿠폰", CouponType.FIXED, -100L, null, FUTURE))
            );
        }

        @DisplayName("RATE 타입에서 value가 1 미만이거나 100 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRateValueIsOutOfRange() {
            // act & assert
            assertAll(
                () -> assertThrows(CoreException.class, () -> new CouponModel("할인쿠폰", CouponType.RATE, 0L, null, FUTURE)),
                () -> assertThrows(CoreException.class, () -> new CouponModel("할인쿠폰", CouponType.RATE, 101L, null, FUTURE))
            );
        }

        @DisplayName("expiredAt이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiredAtIsNull() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                new CouponModel("할인쿠폰", CouponType.FIXED, 1000L, null, null)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("isExpired를 호출할 때,")
    @Nested
    class IsExpired {

        @DisplayName("expiredAt이 현재 시각 이전이면, true를 반환한다.")
        @Test
        void returnsTrue_whenExpiredAtIsInThePast() {
            // arrange
            CouponModel coupon = new CouponModel("할인쿠폰", CouponType.FIXED, 1000L, null, PAST);

            // act & assert
            assertThat(coupon.isExpired()).isTrue();
        }

        @DisplayName("expiredAt이 현재 시각 이후이면, false를 반환한다.")
        @Test
        void returnsFalse_whenExpiredAtIsInTheFuture() {
            // arrange
            CouponModel coupon = new CouponModel("할인쿠폰", CouponType.FIXED, 1000L, null, FUTURE);

            // act & assert
            assertThat(coupon.isExpired()).isFalse();
        }
    }

    @DisplayName("calculateDiscount를 호출할 때,")
    @Nested
    class CalculateDiscount {

        @DisplayName("FIXED 타입에서 orderAmount가 value보다 크면, value를 반환한다.")
        @Test
        void returnsValue_whenFixedAndOrderAmountIsGreaterThanValue() {
            // arrange
            CouponModel coupon = new CouponModel("할인쿠폰", CouponType.FIXED, 1000L, null, FUTURE);

            // act & assert
            assertThat(coupon.calculateDiscount(5000L)).isEqualTo(1000L);
        }

        @DisplayName("FIXED 타입에서 orderAmount가 value보다 작으면, orderAmount를 반환한다.")
        @Test
        void returnsOrderAmount_whenFixedAndOrderAmountIsLessThanValue() {
            // arrange
            CouponModel coupon = new CouponModel("할인쿠폰", CouponType.FIXED, 5000L, null, FUTURE);

            // act & assert
            assertThat(coupon.calculateDiscount(3000L)).isEqualTo(3000L);
        }

        @DisplayName("RATE 타입에서 소수점은 버림 처리된다.")
        @Test
        void returnsFlooredDiscount_whenRateType() {
            // arrange
            CouponModel coupon = new CouponModel("30% 할인", CouponType.RATE, 30L, null, FUTURE);

            // act: 10001 * 30 / 100 = 3000.3 → 3000 (정수 나눗셈으로 버림)
            long discount = coupon.calculateDiscount(10001L);

            // assert
            assertThat(discount).isEqualTo(3000L);
        }

        @DisplayName("minOrderAmount 미충족이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderAmountIsBelowMinOrderAmount() {
            // arrange
            CouponModel coupon = new CouponModel("할인쿠폰", CouponType.FIXED, 1000L, 10000L, FUTURE);

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                coupon.calculateDiscount(5000L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("minOrderAmount가 null이면, 금액 조건 없이 할인이 계산된다.")
        @Test
        void calculatesDiscount_whenMinOrderAmountIsNull() {
            // arrange
            CouponModel coupon = new CouponModel("할인쿠폰", CouponType.FIXED, 1000L, null, FUTURE);

            // act & assert
            assertThat(coupon.calculateDiscount(500L)).isEqualTo(500L);
        }

        @DisplayName("FIXED 타입에서 orderAmount가 value와 같으면, orderAmount를 반환한다.")
        @Test
        void returnsOrderAmount_whenFixedValueEqualsOrderAmount() {
            // arrange
            CouponModel coupon = new CouponModel("1000원 할인", CouponType.FIXED, 1000L, null, FUTURE);

            // act & assert
            assertThat(coupon.calculateDiscount(1000L)).isEqualTo(1000L);
        }

        @DisplayName("RATE 100% 쿠폰이면, 전액 할인되어 orderAmount를 반환한다.")
        @Test
        void returnsFullOrderAmount_whenRateIs100Percent() {
            // arrange
            CouponModel coupon = new CouponModel("전액 할인", CouponType.RATE, 100L, null, FUTURE);

            // act & assert
            assertThat(coupon.calculateDiscount(10000L)).isEqualTo(10000L);
        }
    }
}
