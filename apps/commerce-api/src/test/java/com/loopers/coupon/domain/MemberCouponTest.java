package com.loopers.coupon.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberCouponTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZonedDateTime NOW = ZonedDateTime.now(SEOUL);
    private static final ZonedDateTime FUTURE = NOW.plusDays(1);
    private static final ZonedDateTime PAST = NOW.minusDays(1);

    private MemberCoupon available(ZonedDateTime expiredAt) {
        return new MemberCoupon(1L, 10L, expiredAt, NOW);
    }

    @DisplayName("쿠폰을 사용할 때,")
    @Nested
    class Use {
        @DisplayName("AVAILABLE 상태이면 USED 로 전이되고 주문/사용시각이 기록된다.")
        @Test
        void transitionsToUsed() {
            MemberCoupon coupon = available(FUTURE);
            coupon.use(100L, NOW);
            assertThat(coupon.getState()).isEqualTo(CouponState.USED);
            assertThat(coupon.getOrderId()).isEqualTo(100L);
            assertThat(coupon.getUsedAt()).isEqualTo(NOW);
        }

        @DisplayName("이미 사용된 쿠폰이면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyUsed() {
            MemberCoupon coupon = available(FUTURE);
            coupon.use(100L, NOW);
            CoreException result = assertThrows(CoreException.class, () -> coupon.use(101L, NOW));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("만료된 쿠폰이면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenExpired() {
            MemberCoupon coupon = available(PAST);
            CoreException result = assertThrows(CoreException.class, () -> coupon.use(100L, NOW));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("소유자 검증은 회원 ID 일치 여부로 판단한다.")
    @Test
    void isOwnedBy() {
        MemberCoupon coupon = available(FUTURE);
        assertThat(coupon.isOwnedBy(1L)).isTrue();
        assertThat(coupon.isOwnedBy(2L)).isFalse();
    }

    @DisplayName("표시 상태는 만료 시각이 지난 AVAILABLE 을 EXPIRED 로 보여준다(저장값은 유지).")
    @Test
    void currentState_reflectsExpiry() {
        MemberCoupon coupon = available(PAST);
        assertThat(coupon.currentState(NOW)).isEqualTo(CouponState.EXPIRED);
        assertThat(coupon.getState()).isEqualTo(CouponState.AVAILABLE);
    }
}
