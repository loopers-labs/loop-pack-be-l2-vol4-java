package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class UserCouponTest {

    private static final ZonedDateTime USED_AT = ZonedDateTime.parse("2026-05-31T12:00:00+09:00");

    @DisplayName("사용자 ID와 쿠폰 템플릿 ID가 주어지면, 사용 가능한 유저 쿠폰을 발급한다.")
    @Test
    void issuesUserCoupon_whenUserIdAndCouponTemplateIdAreProvided() {
        // arrange
        Long userId = 1L;
        Long couponTemplateId = 10L;

        // act
        UserCoupon userCoupon = UserCoupon.issue(userId, couponTemplateId);

        // assert
        assertAll(
            () -> assertThat(userCoupon.getUserId()).isEqualTo(userId),
            () -> assertThat(userCoupon.getCouponTemplateId()).isEqualTo(couponTemplateId),
            () -> assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE),
            () -> assertThat(userCoupon.getUsedAt()).isNull()
        );
    }

    @DisplayName("유저 쿠폰을 사용하면, USED 상태와 사용 일시를 기록한다.")
    @Test
    void marksUsed_whenAvailableCouponIsUsed() {
        // arrange
        Long userId = 1L;
        UserCoupon userCoupon = UserCoupon.issue(userId, 10L);

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
        UserCoupon userCoupon = UserCoupon.issue(userId, 10L);
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
        UserCoupon userCoupon = UserCoupon.issue(1L, 10L);
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
        Long couponTemplateId = 10L;

        // act & assert
        assertThatThrownBy(() -> UserCoupon.issue(userId, couponTemplateId))
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

        // act & assert
        assertThatThrownBy(() -> UserCoupon.issue(userId, couponTemplateId))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("쿠폰 사용 일시가 없으면, BAD_REQUEST 예외를 던지고 상태를 유지한다.")
    @Test
    void throwsBadRequest_whenUsedAtIsNull() {
        // arrange
        Long userId = 1L;
        UserCoupon userCoupon = UserCoupon.issue(userId, 10L);
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
        UserCoupon userCoupon = UserCoupon.issue(1L, 10L);

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
        UserCoupon userCoupon = UserCoupon.issue(userId, 10L);
        userCoupon.expire();

        // act & assert
        assertThatThrownBy(() -> userCoupon.use(userId, USED_AT))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.CONFLICT);
    }
}
