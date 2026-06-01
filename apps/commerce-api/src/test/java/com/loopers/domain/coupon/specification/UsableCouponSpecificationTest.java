package com.loopers.domain.coupon.specification;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.policy.FixedCouponDiscountPolicy;
import com.loopers.domain.coupon.vo.CouponMoney;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsableCouponSpecificationTest {

    private static final String COUPON_NAME = "1주년 쿠폰";
    private static final Long USER_ID = 1L;
    private static final Long COUPON_TEMPLATE_ID = 10L;
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-06-01T12:00:00+09:00");
    private static final FixedCouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();

    private final UsableCouponSpecification specification = new UsableCouponSpecification();

    @DisplayName("사용 가능한 유저 쿠폰과 적용 가능한 쿠폰 템플릿이면 쿠폰 사용 조건을 만족한다.")
    @Test
    void returnsTrue_whenCouponCanBeUsed() {
        // arrange
        UserCoupon userCoupon = UserCoupon.issue(USER_ID, COUPON_TEMPLATE_ID);
        CouponTemplate couponTemplate = createCouponTemplate(10_000L);
        CouponMoney orderAmount = CouponMoney.of(12_000L);

        // act
        boolean satisfied = specification.isSatisfiedBy(couponUseAttempt(userCoupon, couponTemplate, USER_ID, orderAmount));

        // assert
        assertThat(satisfied).isTrue();
    }

    @DisplayName("다른 사용자의 유저 쿠폰이면 쿠폰 사용 조건을 만족하지 않는다.")
    @Test
    void returnsFalse_whenCouponBelongsToOtherUser() {
        // arrange
        UserCoupon userCoupon = UserCoupon.issue(USER_ID, COUPON_TEMPLATE_ID);
        CouponTemplate couponTemplate = createCouponTemplate(10_000L);
        Long otherUserId = 2L;
        CouponMoney orderAmount = CouponMoney.of(12_000L);

        // act
        boolean satisfied = specification.isSatisfiedBy(couponUseAttempt(userCoupon, couponTemplate, otherUserId, orderAmount));

        // assert
        assertThat(satisfied).isFalse();
    }

    @DisplayName("이미 사용된 유저 쿠폰이면 쿠폰 사용 조건을 만족하지 않는다.")
    @Test
    void returnsFalse_whenCouponIsAlreadyUsed() {
        // arrange
        UserCoupon userCoupon = UserCoupon.issue(USER_ID, COUPON_TEMPLATE_ID);
        userCoupon.use(USER_ID, NOW);
        CouponTemplate couponTemplate = createCouponTemplate(10_000L);
        CouponMoney orderAmount = CouponMoney.of(12_000L);

        // act
        boolean satisfied = specification.isSatisfiedBy(couponUseAttempt(userCoupon, couponTemplate, USER_ID, orderAmount));

        // assert
        assertThat(satisfied).isFalse();
    }

    @DisplayName("주문 금액이 최소 주문 금액보다 작으면 쿠폰 사용 조건을 만족하지 않는다.")
    @Test
    void returnsFalse_whenOrderAmountIsLessThanMinimum() {
        // arrange
        UserCoupon userCoupon = UserCoupon.issue(USER_ID, COUPON_TEMPLATE_ID);
        CouponTemplate couponTemplate = createCouponTemplate(10_000L);
        CouponMoney orderAmount = CouponMoney.of(9_999L);

        // act
        boolean satisfied = specification.isSatisfiedBy(couponUseAttempt(userCoupon, couponTemplate, USER_ID, orderAmount));

        // assert
        assertThat(satisfied).isFalse();
    }

    @DisplayName("쿠폰 템플릿이 만료되었으면 쿠폰 사용 조건을 만족하지 않는다.")
    @Test
    void returnsFalse_whenCouponTemplateIsExpired() {
        // arrange
        UserCoupon userCoupon = UserCoupon.issue(USER_ID, COUPON_TEMPLATE_ID);
        CouponTemplate couponTemplate = CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            NOW.minusSeconds(1),
            FIXED_POLICY
        );
        CouponMoney orderAmount = CouponMoney.of(12_000L);

        // act
        boolean satisfied = specification.isSatisfiedBy(couponUseAttempt(userCoupon, couponTemplate, USER_ID, orderAmount));

        // assert
        assertThat(satisfied).isFalse();
    }

    @DisplayName("다른 사용자의 유저 쿠폰이면 FORBIDDEN 예외를 던진다.")
    @Test
    void throwsForbidden_whenCouponBelongsToOtherUser() {
        // arrange
        UserCoupon userCoupon = UserCoupon.issue(USER_ID, COUPON_TEMPLATE_ID);
        CouponTemplate couponTemplate = createCouponTemplate(10_000L);
        Long otherUserId = 2L;
        CouponMoney orderAmount = CouponMoney.of(12_000L);

        // act & assert
        assertThatThrownBy(() -> specification.validateUsable(couponUseAttempt(userCoupon, couponTemplate, otherUserId, orderAmount)))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.FORBIDDEN);
    }

    @DisplayName("주문 금액이 최소 주문 금액보다 작으면 CONFLICT 예외를 던진다.")
    @Test
    void throwsConflict_whenOrderAmountIsLessThanMinimum() {
        // arrange
        UserCoupon userCoupon = UserCoupon.issue(USER_ID, COUPON_TEMPLATE_ID);
        CouponTemplate couponTemplate = createCouponTemplate(10_000L);
        CouponMoney orderAmount = CouponMoney.of(9_999L);

        // act & assert
        assertThatThrownBy(() -> specification.validateUsable(couponUseAttempt(userCoupon, couponTemplate, USER_ID, orderAmount)))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.CONFLICT);
    }

    private CouponUseAttempt couponUseAttempt(
        UserCoupon userCoupon,
        CouponTemplate couponTemplate,
        Long userId,
        CouponMoney orderAmount
    ) {
        return new CouponUseAttempt(userCoupon, couponTemplate, userId, orderAmount, NOW);
    }

    private CouponTemplate createCouponTemplate(Long minimumOrderAmount) {
        return CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            minimumOrderAmount,
            EXPIRED_AT,
            FIXED_POLICY
        );
    }
}
