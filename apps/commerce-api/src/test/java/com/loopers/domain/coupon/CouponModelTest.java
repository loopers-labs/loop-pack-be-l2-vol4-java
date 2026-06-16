package com.loopers.domain.coupon;

import com.loopers.domain.coupon.enums.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponModelTest {

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);
    private static final CouponDiscount VALID_RATE_DISCOUNT = new CouponDiscount(CouponType.RATE, 10L, null);
    private static final CouponExpiry FUTURE_EXPIRY = new CouponExpiry(FUTURE);

    private CouponModel createFixedCoupon(long value, Long minOrderAmount) {
        return new CouponModel("테스트 쿠폰", new CouponDiscount(CouponType.FIXED, value, minOrderAmount), FUTURE_EXPIRY);
    }

    private CouponModel createRateCoupon(long value) {
        return new CouponModel("테스트 쿠폰", new CouponDiscount(CouponType.RATE, value, null), FUTURE_EXPIRY);
    }

    private CouponModel createExpiredCoupon() {
        return new CouponModel("테스트 쿠폰", VALID_RATE_DISCOUNT, new CouponExpiry(PAST));
    }

    @DisplayName("쿠폰 생성 시,")
    @Nested
    class Create {

        @DisplayName("이름이 null, 빈 값, 공백이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void throwsBadRequest_whenNameIsNullOrBlank(String name) {
            CoreException exception = assertThrows(CoreException.class, () ->
                new CouponModel(name, VALID_RATE_DISCOUNT, FUTURE_EXPIRY)
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 100자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameExceeds100Characters() {
            String longName = "a".repeat(101);

            CoreException exception = assertThrows(CoreException.class, () ->
                new CouponModel(longName, VALID_RATE_DISCOUNT, FUTURE_EXPIRY)
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인 정보가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDiscountIsNull() {
            CoreException exception = assertThrows(CoreException.class, () ->
                new CouponModel("테스트 쿠폰", null, FUTURE_EXPIRY)
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료 정보가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiryIsNull() {
            CoreException exception = assertThrows(CoreException.class, () ->
                new CouponModel("테스트 쿠폰", VALID_RATE_DISCOUNT, null)
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("정액 쿠폰 생성 시,")
    @Nested
    class CreateFixed {

        @DisplayName("할인 금액이 0 이하면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -1000L})
        void throwsBadRequest_whenValueIsZeroOrNegative(long value) {
            CoreException exception = assertThrows(CoreException.class, () ->
                new CouponDiscount(CouponType.FIXED, value, 10000L)
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 값이면, 정상 생성된다.")
        @Test
        void createsSuccessfully_whenValueIsValid() {
            CouponModel coupon = createFixedCoupon(1000L, 10000L);

            assertThat(coupon).isNotNull();
        }
    }

    @DisplayName("정률 쿠폰 생성 시,")
    @Nested
    class CreateRate {

        @DisplayName("할인율이 0 이하면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -100L})
        void throwsBadRequest_whenValueIsZeroOrNegative(long value) {
            CoreException exception = assertThrows(CoreException.class, () ->
                new CouponDiscount(CouponType.RATE, value, null)
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인율이 100을 초과하면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = {101L, 200L})
        void throwsBadRequest_whenValueExceeds100(long value) {
            CoreException exception = assertThrows(CoreException.class, () ->
                new CouponDiscount(CouponType.RATE, value, null)
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 값이면, 정상 생성된다.")
        @Test
        void createsSuccessfully_whenValueIsValid() {
            CouponModel coupon = createRateCoupon(10L);

            assertThat(coupon).isNotNull();
        }
    }

    @DisplayName("만료 여부 확인 시,")
    @Nested
    class IsExpired {

        @DisplayName("만료일이 지났으면, true를 반환한다.")
        @Test
        void returnsTrue_whenExpiredAtIsInThePast() {
            CouponModel coupon = createExpiredCoupon();

            assertThat(coupon.isExpired()).isTrue();
        }

        @DisplayName("만료일이 지나지 않았으면, false를 반환한다.")
        @Test
        void returnsFalse_whenExpiredAtIsInTheFuture() {
            CouponModel coupon = createRateCoupon(10L);

            assertThat(coupon.isExpired()).isFalse();
        }
    }

    @DisplayName("만료일 연장 시,")
    @Nested
    class ExtendExpiredAt {

        @DisplayName("현재 만료일 이후 날짜로 연장하면, 만료일이 변경된다.")
        @Test
        void extendsExpiredAt_whenNewDateIsAfterCurrent() {
            CouponModel coupon = createRateCoupon(10L);
            ZonedDateTime newExpiredAt = FUTURE.plusDays(10);

            coupon.extendExpiredAt(newExpiredAt);

            assertThat(coupon.getExpiry().getExpiredAt()).isEqualTo(newExpiredAt);
        }

        @DisplayName("현재 만료일과 동일한 날짜로 요청하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewDateIsEqualToCurrent() {
            CouponModel coupon = createRateCoupon(10L);

            CoreException exception = assertThrows(CoreException.class, () ->
                coupon.extendExpiredAt(FUTURE)
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("현재 만료일보다 이전 날짜로 요청하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewDateIsBeforeCurrent() {
            CouponModel coupon = createRateCoupon(10L);

            CoreException exception = assertThrows(CoreException.class, () ->
                coupon.extendExpiredAt(FUTURE.minusDays(1))
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("만료 여부 검증 시,")
    @Nested
    class ValidateNotExpired {

        @DisplayName("만료되지 않은 쿠폰이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenCouponIsNotExpired() {
            CouponModel coupon = createRateCoupon(10L);

            assertDoesNotThrow(coupon::validateNotExpired);
        }

        @DisplayName("만료된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIsExpired() {
            CouponModel coupon = createExpiredCoupon();

            CoreException exception = assertThrows(CoreException.class, coupon::validateNotExpired);

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("삭제 가능 여부 검증 시,")
    @Nested
    class ValidateDeletable {

        @DisplayName("만료된 쿠폰이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenCouponIsExpired() {
            CouponModel coupon = createExpiredCoupon();

            coupon.validateDeletable();
        }

        @DisplayName("만료되지 않은 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsCouponNotDeletable_whenCouponIsNotExpired() {
            CouponModel coupon = createRateCoupon(10L);

            CoreException exception = assertThrows(CoreException.class, coupon::validateDeletable);

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("이름 수정 시,")
    @Nested
    class UpdateName {

        @DisplayName("유효한 이름이면, 이름이 변경된다.")
        @Test
        void updatesName_whenNameIsValid() {
            CouponModel coupon = createRateCoupon(10L);

            coupon.updateName("새 쿠폰 이름");

            assertThat(coupon.getName()).isEqualTo("새 쿠폰 이름");
        }

        @DisplayName("이름이 null, 빈 값, 공백이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void throwsBadRequest_whenNameIsNullOrBlank(String name) {
            CouponModel coupon = createRateCoupon(10L);

            CoreException exception = assertThrows(CoreException.class, () -> coupon.updateName(name));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 100자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameExceeds100Characters() {
            CouponModel coupon = createRateCoupon(10L);
            String longName = "a".repeat(101);

            CoreException exception = assertThrows(CoreException.class, () -> coupon.updateName(longName));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("할인 금액 계산 시,")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액 쿠폰은 고정 금액을 할인한다.")
        @Test
        void returnsFixedValue_whenTypeIsFixed() {
            CouponModel coupon = createFixedCoupon(3000L, 5000L);

            long discount = coupon.calculateDiscount(10000L);

            assertThat(discount).isEqualTo(3000L);
        }

        @DisplayName("정액 쿠폰의 할인 금액이 주문 금액을 초과하면, 주문 금액을 반환한다.")
        @Test
        void returnsOrderAmount_whenFixedDiscountExceedsOrderAmount() {
            CouponModel coupon = createFixedCoupon(5000L, 3000L);

            long discount = coupon.calculateDiscount(3000L);

            assertThat(discount).isEqualTo(3000L);
        }

        @DisplayName("정률 쿠폰은 주문 금액의 비율만큼 할인한다.")
        @Test
        void returnsRateDiscount_whenTypeIsRate() {
            CouponModel coupon = createRateCoupon(10L);

            long discount = coupon.calculateDiscount(10000L);

            assertThat(discount).isEqualTo(1000L);
        }

        @DisplayName("최소 주문 금액 미충족 시, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsCouponMinOrderAmountNotMet_whenOrderAmountIsBelowMinimum() {
            CouponModel coupon = createFixedCoupon(3000L, 10000L);

            CoreException exception = assertThrows(CoreException.class, () ->
                coupon.calculateDiscount(5000L)
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
