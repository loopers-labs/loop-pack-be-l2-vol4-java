package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserCouponTest {

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    class Create {
        @DisplayName("발급된 쿠폰은 AVAILABLE 상태로 시작한다.")
        @Test
        void startsAsAvailable() {
            // act
            UserCoupon userCoupon = new UserCoupon(1L, 100L);

            // assert
            assertThat(userCoupon.getUserId()).isEqualTo(1L);
            assertThat(userCoupon.getCouponId()).isEqualTo(100L);
            assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        }
    }

    @DisplayName("노출 상태를 계산할 때, ")
    @Nested
    class ResolveStatus {
        @DisplayName("만료일이 현재보다 미래이고 미사용이면, AVAILABLE 을 반환한다.")
        @Test
        void returnsAvailable_whenNotExpiredAndUnused() {
            // arrange
            UserCoupon userCoupon = new UserCoupon(1L, 100L);
            LocalDateTime now = LocalDateTime.of(2026, 6, 8, 0, 0);
            LocalDateTime expiredAt = now.plusDays(1);

            // act
            CouponStatus status = userCoupon.resolveStatus(expiredAt, now);

            // assert
            assertThat(status).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("만료일이 현재보다 과거이면, EXPIRED 를 반환한다.")
        @Test
        void returnsExpired_whenPastExpiredAt() {
            // arrange
            UserCoupon userCoupon = new UserCoupon(1L, 100L);
            LocalDateTime now = LocalDateTime.of(2026, 6, 8, 0, 0);
            LocalDateTime expiredAt = now.minusDays(1);

            // act
            CouponStatus status = userCoupon.resolveStatus(expiredAt, now);

            // assert
            assertThat(status).isEqualTo(CouponStatus.EXPIRED);
        }
    }
}
