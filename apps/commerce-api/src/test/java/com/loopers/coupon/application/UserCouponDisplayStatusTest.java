package com.loopers.coupon.application;

import com.loopers.coupon.domain.UserCouponStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserCouponDisplayStatusTest {

    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");

    @DisplayName("저장 상태가 AVAILABLE이고 만료 시각이 현재 시각과 같으면, EXPIRED로 보여준다.")
    @Test
    void returnsExpired_whenAvailableCouponExpiresAtCurrentTime() {
        // arrange
        UserCouponDisplayStatus displayStatus = UserCouponDisplayStatus.fromUserCouponStatus(UserCouponStatus.AVAILABLE, EXPIRED_AT);

        // act
        UserCouponStatus status = displayStatus.toDisplayStatus(EXPIRED_AT);

        // assert
        assertThat(status).isEqualTo(UserCouponStatus.EXPIRED);
    }

    @DisplayName("저장 상태가 AVAILABLE이고 만료 시각이 지나지 않았으면, AVAILABLE로 보여준다.")
    @Test
    void returnsAvailable_whenAvailableCouponIsNotExpired() {
        // arrange
        UserCouponDisplayStatus displayStatus = UserCouponDisplayStatus.fromUserCouponStatus(UserCouponStatus.AVAILABLE, EXPIRED_AT);
        ZonedDateTime now = EXPIRED_AT.minusNanos(1);

        // act
        UserCouponStatus status = displayStatus.toDisplayStatus(now);

        // assert
        assertThat(status).isEqualTo(UserCouponStatus.AVAILABLE);
    }

    @DisplayName("저장 상태가 USED이면 만료 시각이 지나도, USED로 보여준다.")
    @Test
    void returnsUsed_whenStoredStatusIsUsedEvenIfExpired() {
        // arrange
        UserCouponDisplayStatus displayStatus = UserCouponDisplayStatus.fromUserCouponStatus(UserCouponStatus.USED, EXPIRED_AT);
        ZonedDateTime now = EXPIRED_AT.plusNanos(1);

        // act
        UserCouponStatus status = displayStatus.toDisplayStatus(now);

        // assert
        assertThat(status).isEqualTo(UserCouponStatus.USED);
    }
}
