package com.loopers.coupon.domain;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class UserCouponTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-06-09T12:00:00+09:00");
    private static final ZonedDateTime FUTURE = ZonedDateTime.parse("2030-12-31T23:59:59+09:00");
    private static final ZonedDateTime PAST = ZonedDateTime.parse("2020-01-01T00:00:00+09:00");

    private UserCoupon issued(CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return Coupon.create("쿠폰", type, value, minOrderAmount, expiredAt).issueTo(USER_ID);
    }

    @Test
    @DisplayName("issueTo 로 발급하면 소유자·정책 스냅샷을 복사하고 상태는 AVAILABLE 이다")
    void givenTemplate_whenIssueTo_thenCopiesSnapshotWithAvailableStatus() {
        UserCoupon userCoupon = issued(CouponType.FIXED, 3_000L, 10_000L, FUTURE);

        assertAll(
                () -> assertThat(userCoupon.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE),
                () -> assertThat(userCoupon.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(userCoupon.getValue()).isEqualTo(3_000L),
                () -> assertThat(userCoupon.getMinOrderAmount()).isEqualTo(10_000L),
                () -> assertThat(userCoupon.getExpiredAt()).isEqualTo(FUTURE),
                () -> assertThat(userCoupon.getUsedAt()).isNull()
        );
    }

    @Test
    @DisplayName("calculateDiscount 는 스냅샷 타입/값으로 할인액을 계산한다")
    void givenRateCoupon_whenCalculateDiscount_thenDelegatesToType() {
        UserCoupon userCoupon = issued(CouponType.RATE, 10L, null, FUTURE);

        assertThat(userCoupon.calculateDiscount(12_345L).value()).isEqualTo(1_234L);
    }

    @Test
    @DisplayName("use 에 성공하면 USED 로 전이하고 사용 시각을 기록한다")
    void givenAvailableCoupon_whenUse_thenTransitionsToUsed() {
        UserCoupon userCoupon = issued(CouponType.FIXED, 3_000L, 10_000L, FUTURE);

        userCoupon.use(USER_ID, 10_000L, NOW);

        assertAll(
                () -> assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.USED),
                () -> assertThat(userCoupon.getUsedAt()).isEqualTo(NOW)
        );
    }

    @Test
    @DisplayName("use 시 소유자가 다르면 COUPON_NOT_OWNED 가 발생한다")
    void givenOtherOwner_whenUse_thenThrowsNotOwned() {
        UserCoupon userCoupon = issued(CouponType.FIXED, 3_000L, null, FUTURE);

        assertThatThrownBy(() -> userCoupon.use(OTHER_USER_ID, 10_000L, NOW))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(CouponErrorCode.COUPON_NOT_OWNED);
    }

    @Test
    @DisplayName("use 시 이미 사용된 쿠폰이면 COUPON_ALREADY_USED 가 발생한다")
    void givenUsedCoupon_whenUse_thenThrowsAlreadyUsed() {
        UserCoupon userCoupon = issued(CouponType.FIXED, 3_000L, null, FUTURE);
        userCoupon.use(USER_ID, 10_000L, NOW);

        assertThatThrownBy(() -> userCoupon.use(USER_ID, 10_000L, NOW))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(CouponErrorCode.COUPON_ALREADY_USED);
    }

    @Test
    @DisplayName("use 시 만료된 쿠폰이면 COUPON_EXPIRED 가 발생한다")
    void givenExpiredCoupon_whenUse_thenThrowsExpired() {
        UserCoupon userCoupon = issued(CouponType.FIXED, 3_000L, null, PAST);

        assertThatThrownBy(() -> userCoupon.use(USER_ID, 10_000L, NOW))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(CouponErrorCode.COUPON_EXPIRED);
    }

    @Test
    @DisplayName("use 시 최소 주문 금액에 미달하면 COUPON_MIN_ORDER_NOT_MET 가 발생한다")
    void givenBelowMinOrder_whenUse_thenThrowsMinOrderNotMet() {
        UserCoupon userCoupon = issued(CouponType.FIXED, 3_000L, 10_000L, FUTURE);

        assertThatThrownBy(() -> userCoupon.use(USER_ID, 9_999L, NOW))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(CouponErrorCode.COUPON_MIN_ORDER_NOT_MET);
    }

    @Test
    @DisplayName("restore 는 USED 를 AVAILABLE 로 되돌리고 사용 시각을 비운다")
    void givenUsedCoupon_whenRestore_thenBackToAvailable() {
        UserCoupon userCoupon = issued(CouponType.FIXED, 3_000L, null, FUTURE);
        userCoupon.use(USER_ID, 10_000L, NOW);

        userCoupon.restore();

        assertAll(
                () -> assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE),
                () -> assertThat(userCoupon.getUsedAt()).isNull()
        );
    }

    @Test
    @DisplayName("displayStatus 는 사용된 쿠폰을 USED 로 반환한다")
    void givenUsedCoupon_whenDisplayStatus_thenUsed() {
        UserCoupon userCoupon = issued(CouponType.FIXED, 3_000L, null, FUTURE);
        userCoupon.use(USER_ID, 10_000L, NOW);

        assertThat(userCoupon.displayStatus(NOW)).isEqualTo(CouponStatus.USED);
    }

    @Test
    @DisplayName("displayStatus 는 만료된 AVAILABLE 쿠폰을 EXPIRED 로 파생한다")
    void givenExpiredAvailable_whenDisplayStatus_thenExpired() {
        UserCoupon userCoupon = issued(CouponType.FIXED, 3_000L, null, PAST);

        assertThat(userCoupon.displayStatus(NOW)).isEqualTo(CouponStatus.EXPIRED);
    }

    @Test
    @DisplayName("displayStatus 는 유효한 AVAILABLE 쿠폰을 AVAILABLE 로 반환한다")
    void givenValidAvailable_whenDisplayStatus_thenAvailable() {
        UserCoupon userCoupon = issued(CouponType.FIXED, 3_000L, null, FUTURE);

        assertThat(userCoupon.displayStatus(NOW)).isEqualTo(CouponStatus.AVAILABLE);
    }
}
