package com.loopers.domain.coupon;

import com.loopers.domain.coupon.policy.FixedCouponDiscountPolicy;
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

class UserCouponTest {

    private static final String COUPON_NAME = "1주년 쿠폰";
    private static final Long COUPON_TEMPLATE_ID = 10L;
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");
    private static final ZonedDateTime USED_AT = ZonedDateTime.parse("2026-05-31T12:00:00+09:00");
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-06-01T12:00:00+09:00");
    private static final FixedCouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();

    @DisplayName("사용자 ID와 쿠폰 템플릿이 주어지면, 발급 당시 쿠폰 조건을 스냅샷으로 가진 유저 쿠폰을 발급한다.")
    @Test
    void issuesUserCouponWithSnapshot_whenUserIdAndCouponTemplateAreProvided() {
        // arrange
        Long userId = 1L;
        CouponTemplate couponTemplate = createCouponTemplate();

        // act
        UserCoupon userCoupon = UserCoupon.issue(userId, COUPON_TEMPLATE_ID, couponTemplate);

        // assert
        assertAll(
            () -> assertThat(userCoupon.getUserId()).isEqualTo(userId),
            () -> assertThat(userCoupon.getCouponTemplateId()).isEqualTo(COUPON_TEMPLATE_ID),
            () -> assertThat(userCoupon.getName()).isEqualTo(COUPON_NAME),
            () -> assertThat(userCoupon.getType()).isEqualTo(CouponType.FIXED),
            () -> assertThat(userCoupon.getDiscountValue().value()).isEqualTo(2_000L),
            () -> assertThat(userCoupon.getMinimumOrderAmount().value()).isEqualTo(10_000L),
            () -> assertThat(userCoupon.getExpiration().expiredAt()).isEqualTo(EXPIRED_AT),
            () -> assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE),
            () -> assertThat(userCoupon.getUsedAt()).isNull()
        );
    }

    @DisplayName("유저 쿠폰을 사용하면, USED 상태와 사용 일시를 기록한다.")
    @Test
    void marksUsed_whenAvailableCouponIsUsed() {
        // arrange
        Long userId = 1L;
        UserCoupon userCoupon = issueCoupon(userId);

        // act
        userCoupon.use(userId, USED_AT);

        // assert
        assertAll(
            () -> assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED),
            () -> assertThat(userCoupon.getUsedAt()).isEqualTo(USED_AT)
        );
    }

    @DisplayName("이미 사용된 유저 쿠폰을 다시 사용하면, CONFLICT 예외를 던지고 상태를 유지한다.")
    @Test
    void throwsConflict_whenUsedCouponIsUsedAgain() {
        // arrange
        Long userId = 1L;
        UserCoupon userCoupon = issueCoupon(userId);
        userCoupon.use(userId, USED_AT);

        // act & assert
        assertAll(
            () -> assertThatThrownBy(() -> userCoupon.use(userId, USED_AT.plusMinutes(1)))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.CONFLICT),
            () -> assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED),
            () -> assertThat(userCoupon.getUsedAt()).isEqualTo(USED_AT)
        );
    }

    @DisplayName("다른 사용자의 유저 쿠폰을 사용하면, FORBIDDEN 예외를 던진다.")
    @Test
    void throwsForbidden_whenCouponBelongsToOtherUser() {
        // arrange
        UserCoupon userCoupon = issueCoupon(1L);
        Long otherUserId = 2L;

        // act & assert
        assertThatThrownBy(() -> userCoupon.use(otherUserId, USED_AT))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.FORBIDDEN);
    }

    @DisplayName("사용자 ID가 없으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenUserIdIsNull() {
        // arrange
        Long userId = null;
        CouponTemplate couponTemplate = createCouponTemplate();

        // act & assert
        assertThatThrownBy(() -> UserCoupon.issue(userId, COUPON_TEMPLATE_ID, couponTemplate))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("쿠폰 템플릿 ID가 없으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenCouponTemplateIdIsNull() {
        // arrange
        Long userId = 1L;
        Long couponTemplateId = null;
        CouponTemplate couponTemplate = createCouponTemplate();

        // act & assert
        assertThatThrownBy(() -> UserCoupon.issue(userId, couponTemplateId, couponTemplate))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("쿠폰이 없으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenCouponTemplateIsNull() {
        // arrange
        Long userId = 1L;
        CouponTemplate couponTemplate = null;

        // act & assert
        assertThatThrownBy(() -> UserCoupon.issue(userId, COUPON_TEMPLATE_ID, couponTemplate))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("쿠폰 사용 일시가 없으면, BAD_REQUEST 예외를 던지고 상태를 유지한다.")
    @Test
    void throwsBadRequest_whenUsedAtIsNull() {
        // arrange
        Long userId = 1L;
        UserCoupon userCoupon = issueCoupon(userId);
        ZonedDateTime usedAt = null;

        // act & assert
        assertAll(
            () -> assertThatThrownBy(() -> userCoupon.use(userId, usedAt))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST),
            () -> assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE)
        );
    }

    @DisplayName("사용 가능한 유저 쿠폰을 만료 처리하면, EXPIRED 상태로 변경한다.")
    @Test
    void expiresCoupon_whenCouponIsAvailable() {
        // arrange
        UserCoupon userCoupon = issueCoupon(1L);

        // act
        userCoupon.expire();

        // assert
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.EXPIRED);
    }

    @DisplayName("만료된 유저 쿠폰을 사용하면, CONFLICT 예외를 던진다.")
    @Test
    void throwsConflict_whenExpiredCouponIsUsed() {
        // arrange
        Long userId = 1L;
        UserCoupon userCoupon = issueCoupon(userId);
        userCoupon.expire();

        // act & assert
        assertThatThrownBy(() -> userCoupon.use(userId, USED_AT))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.CONFLICT);
    }

    @DisplayName("사용 가능한 유저 쿠폰에 적용하면, 할인 금액을 계산한다.")
    @Test
    void appliesDiscount_whenCouponIsApplicable() {
        // arrange
        UserCoupon userCoupon = issueCoupon(1L);
        CouponMoney orderAmount = CouponMoney.of(12_000L);

        // act
        CouponDiscount discount = userCoupon.apply(orderAmount, NOW, FIXED_POLICY);

        // assert
        assertThat(discount.discountAmount().value()).isEqualTo(2_000L);
    }

    @DisplayName("주문 금액이 최소 주문 금액보다 작은 쿠폰을 적용하면, CONFLICT 예외를 던진다.")
    @Test
    void throwsConflict_whenOrderAmountIsLessThanMinimum() {
        // arrange
        UserCoupon userCoupon = issueCoupon(1L);
        CouponMoney orderAmount = CouponMoney.of(9_999L);

        // act & assert
        assertThatThrownBy(() -> userCoupon.apply(orderAmount, NOW, FIXED_POLICY))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.CONFLICT);
    }

    @DisplayName("만료일이 지난 유저 쿠폰을 적용하면, CONFLICT 예외를 던진다.")
    @Test
    void throwsConflict_whenIssuedCouponIsExpired() {
        // arrange
        UserCoupon userCoupon = issueCoupon(1L, NOW.minusSeconds(1));
        CouponMoney orderAmount = CouponMoney.of(12_000L);

        // act & assert
        assertThatThrownBy(() -> userCoupon.apply(orderAmount, NOW, FIXED_POLICY))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.CONFLICT);
    }

    private UserCoupon issueCoupon(Long userId) {
        return UserCoupon.issue(userId, COUPON_TEMPLATE_ID, createCouponTemplate());
    }

    private UserCoupon issueCoupon(Long userId, ZonedDateTime expiredAt) {
        return UserCoupon.issue(userId, COUPON_TEMPLATE_ID, createCouponTemplate(expiredAt));
    }

    private CouponTemplate createCouponTemplate() {
        return createCouponTemplate(EXPIRED_AT);
    }

    private CouponTemplate createCouponTemplate(ZonedDateTime expiredAt) {
        return CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            expiredAt,
            FIXED_POLICY
        );
    }
}
