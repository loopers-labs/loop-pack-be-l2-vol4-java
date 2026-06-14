package com.loopers.coupon.domain;

import com.loopers.coupon.domain.policy.CouponDiscountPolicy;
import com.loopers.coupon.domain.policy.FixedCouponDiscountPolicy;
import com.loopers.coupon.domain.policy.RateCouponDiscountPolicy;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class CouponTemplateTest {

    private static final String COUPON_NAME = "1주년 쿠폰";
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");
    private static final CouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();
    private static final CouponDiscountPolicy RATE_POLICY = new RateCouponDiscountPolicy();

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
