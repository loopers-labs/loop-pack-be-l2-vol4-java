package com.loopers.domain.coupon;

import com.loopers.domain.coupon.policy.CouponDiscountPolicy;
import com.loopers.domain.coupon.policy.FixedCouponDiscountPolicy;
import com.loopers.domain.coupon.policy.RateCouponDiscountPolicy;
import com.loopers.domain.coupon.vo.CouponDiscount;
import com.loopers.domain.coupon.vo.CouponMoney;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class CouponTemplateTest {

    private static final String COUPON_NAME = "1주년 쿠폰";
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-05-31T12:00:00+09:00");
    private static final CouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();
    private static final CouponDiscountPolicy RATE_POLICY = new RateCouponDiscountPolicy();

    @DisplayName("정액 쿠폰을 적용하면, 주문 금액에서 정액 할인 금액을 차감한다.")
    @Test
    void appliesFixedDiscount_whenOrderAmountIsEligible() {
        // arrange
        CouponTemplate couponTemplate = CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            EXPIRED_AT,
            FIXED_POLICY
        );
        CouponMoney orderAmount = CouponMoney.of(12_000L);

        // act
        CouponDiscount discount = couponTemplate.apply(orderAmount, NOW, FIXED_POLICY);

        // assert
        assertAll(
            () -> assertThat(discount.orderAmount()).isEqualTo(orderAmount),
            () -> assertThat(discount.discountAmount().value()).isEqualTo(2_000L),
            () -> assertThat(discount.paymentAmount().value()).isEqualTo(10_000L)
        );
    }

    @DisplayName("정액 할인 금액이 주문 금액보다 크면, 최종 결제 금액을 0원으로 제한한다.")
    @Test
    void limitsFinalAmountToZero_whenFixedDiscountIsGreaterThanOrderAmount() {
        // arrange
        CouponTemplate couponTemplate = CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            null,
            EXPIRED_AT,
            FIXED_POLICY
        );
        CouponMoney orderAmount = CouponMoney.of(1_000L);

        // act
        CouponDiscount discount = couponTemplate.apply(orderAmount, NOW, FIXED_POLICY);

        // assert
        assertAll(
            () -> assertThat(discount.discountAmount()).isEqualTo(orderAmount),
            () -> assertThat(discount.paymentAmount().value()).isZero()
        );
    }

    @DisplayName("정률 쿠폰을 적용하면, 원 단위 미만을 절사한 할인 금액을 차감한다.")
    @Test
    void appliesRateDiscountWithFlooring_whenOrderAmountIsEligible() {
        // arrange
        CouponTemplate couponTemplate = CouponTemplate.create(
            COUPON_NAME,
            CouponType.RATE,
            10L,
            10_000L,
            EXPIRED_AT,
            RATE_POLICY
        );
        CouponMoney orderAmount = CouponMoney.of(12_345L);

        // act
        CouponDiscount discount = couponTemplate.apply(orderAmount, NOW, RATE_POLICY);

        // assert
        assertAll(
            () -> assertThat(discount.discountAmount().value()).isEqualTo(1_234L),
            () -> assertThat(discount.paymentAmount().value()).isEqualTo(11_111L)
        );
    }

    @DisplayName("주문 금액이 최소 주문 금액보다 작으면, CONFLICT 예외를 던진다.")
    @Test
    void throwsConflict_whenOrderAmountIsLessThanMinimum() {
        // arrange
        CouponTemplate couponTemplate = CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            EXPIRED_AT,
            FIXED_POLICY
        );
        CouponMoney orderAmount = CouponMoney.of(9_999L);

        // act & assert
        assertThatThrownBy(() -> couponTemplate.apply(orderAmount, NOW, FIXED_POLICY))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.CONFLICT);
    }

    @DisplayName("만료된 쿠폰을 적용하면, CONFLICT 예외를 던진다.")
    @Test
    void throwsConflict_whenCouponIsExpired() {
        // arrange
        CouponTemplate couponTemplate = CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            EXPIRED_AT,
            FIXED_POLICY
        );
        ZonedDateTime now = ZonedDateTime.parse("2027-01-01T00:00:00+09:00");
        CouponMoney orderAmount = CouponMoney.of(12_000L);

        // act & assert
        assertThatThrownBy(() -> couponTemplate.apply(orderAmount, now, FIXED_POLICY))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.CONFLICT);
    }

    @DisplayName("만료된 쿠폰은 발급할 수 없으므로, CONFLICT 예외를 던진다.")
    @Test
    void throwsConflict_whenCouponIsExpiredForIssue() {
        // arrange
        CouponTemplate couponTemplate = CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            EXPIRED_AT,
            FIXED_POLICY
        );

        // act & assert
        assertThatThrownBy(() -> couponTemplate.issue(1L, EXPIRED_AT))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.CONFLICT);
    }

    @DisplayName("정액 쿠폰 금액이 0 이하이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenFixedDiscountValueIsNotPositive() {
        // arrange
        long discountValue = 0L;

        // act & assert
        assertThatThrownBy(() -> CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            discountValue,
            10_000L,
            EXPIRED_AT,
            FIXED_POLICY
        ))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("쿠폰명이 비어 있으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenCouponNameIsBlank() {
        // arrange
        String name = " ";

        // act & assert
        assertThatThrownBy(() -> CouponTemplate.create(name, CouponType.FIXED, 2_000L, 10_000L, EXPIRED_AT, FIXED_POLICY))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("쿠폰 타입이 없으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenCouponTypeIsNull() {
        // arrange
        CouponType type = null;

        // act & assert
        assertThatThrownBy(() -> CouponTemplate.create(COUPON_NAME, type, 2_000L, 10_000L, EXPIRED_AT, FIXED_POLICY))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("정률 쿠폰 비율이 1에서 100 사이가 아니면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenRateDiscountValueIsOutOfRange() {
        // arrange
        long discountValue = 101L;

        // act & assert
        assertThatThrownBy(() -> CouponTemplate.create(
            COUPON_NAME,
            CouponType.RATE,
            discountValue,
            10_000L,
            EXPIRED_AT,
            RATE_POLICY
        ))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("최소 주문 금액이 음수이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenMinOrderAmountIsNegative() {
        // arrange
        Long minimumOrderAmount = -1L;

        // act & assert
        assertThatThrownBy(() -> CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            minimumOrderAmount,
            EXPIRED_AT,
            FIXED_POLICY
        ))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("주문 금액이 없으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenOrderAmountIsNull() {
        // arrange
        CouponTemplate couponTemplate = CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            EXPIRED_AT,
            FIXED_POLICY
        );
        CouponMoney orderAmount = null;

        // act & assert
        assertThatThrownBy(() -> couponTemplate.apply(orderAmount, NOW, FIXED_POLICY))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("쿠폰을 수정하면, 타입을 포함한 모든 필드가 새 값으로 바뀐다.")
    @Test
    void updatesAllFields_includingType() {
        // arrange
        CouponTemplate couponTemplate = CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            EXPIRED_AT,
            FIXED_POLICY
        );
        ZonedDateTime newExpiredAt = ZonedDateTime.parse("2027-06-30T23:59:59+09:00");

        // act
        couponTemplate.update("1주년 10% 할인", CouponType.RATE, 10L, 20_000L, newExpiredAt, RATE_POLICY);

        // assert
        assertAll(
            () -> assertThat(couponTemplate.getName()).isEqualTo("1주년 10% 할인"),
            () -> assertThat(couponTemplate.getType()).isEqualTo(CouponType.RATE),
            () -> assertThat(couponTemplate.getDiscountValue().value()).isEqualTo(10L),
            () -> assertThat(couponTemplate.getMinimumOrderAmount().value()).isEqualTo(20_000L),
            () -> assertThat(couponTemplate.getExpiration().expiredAt()).isEqualTo(newExpiredAt)
        );
    }

    @DisplayName("정률로 수정 시 비율이 1에서 100 사이가 아니면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenUpdatedRateDiscountValueIsOutOfRange() {
        // arrange
        CouponTemplate couponTemplate = CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            EXPIRED_AT,
            FIXED_POLICY
        );

        // act & assert
        assertThatThrownBy(() -> couponTemplate.update(COUPON_NAME, CouponType.RATE, 101L, 10_000L, EXPIRED_AT, RATE_POLICY))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("쿠폰 타입과 할인 정책이 맞지 않으면, INTERNAL_ERROR 예외를 던진다.")
    @Test
    void throwsInternalError_whenCouponPolicyDoesNotMatchType() {
        // arrange
        CouponType type = CouponType.FIXED;

        // act & assert
        assertThatThrownBy(() -> CouponTemplate.create(COUPON_NAME, type, 2_000L, 10_000L, EXPIRED_AT, RATE_POLICY))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.INTERNAL_ERROR);
    }
}
