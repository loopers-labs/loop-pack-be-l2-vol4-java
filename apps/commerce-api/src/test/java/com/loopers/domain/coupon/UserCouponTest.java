package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @DisplayName("쿠폰을 사용할 때, ")
    @Nested
    class Use {
        @DisplayName("사용 가능한 쿠폰이면, USED 상태로 전이된다.")
        @Test
        void transitionsToUsed_whenAvailable() {
            // arrange
            UserCoupon userCoupon = new UserCoupon(1L, 100L);
            LocalDateTime now = LocalDateTime.of(2026, 6, 8, 0, 0);
            LocalDateTime expiredAt = now.plusDays(1);

            // act
            userCoupon.use(expiredAt, now);

            // assert
            assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @DisplayName("이미 사용된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyUsed() {
            // arrange
            UserCoupon userCoupon = new UserCoupon(1L, 100L);
            LocalDateTime now = LocalDateTime.of(2026, 6, 8, 0, 0);
            LocalDateTime expiredAt = now.plusDays(1);
            userCoupon.use(expiredAt, now);

            // act
            CoreException result = assertThrows(CoreException.class, () -> userCoupon.use(expiredAt, now));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpired() {
            // arrange
            UserCoupon userCoupon = new UserCoupon(1L, 100L);
            LocalDateTime now = LocalDateTime.of(2026, 6, 8, 0, 0);
            LocalDateTime expiredAt = now.minusDays(1);

            // act
            CoreException result = assertThrows(CoreException.class, () -> userCoupon.use(expiredAt, now));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
