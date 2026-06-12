package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("domain")
class UserCouponModelTest {

    @DisplayName("유저 쿠폰을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("생성 직후 상태는 AVAILABLE이다.")
        @Test
        void statusIsAvailable_whenCreated() {
            UserCouponModel userCoupon = new UserCouponModel(1L, 10L);

            assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        }
    }

    @DisplayName("쿠폰을 사용할 때, ")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태의 쿠폰을 사용하면 USED 상태로 변경된다.")
        @Test
        void changesStatusToUsed_whenAvailable() {
            UserCouponModel userCoupon = new UserCouponModel(1L, 10L);

            userCoupon.use();

            assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @DisplayName("이미 USED 상태인 쿠폰을 사용하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyUsed() {
            UserCouponModel userCoupon = new UserCouponModel(1L, 1L, 10L, CouponStatus.USED, 0L, null, null);

            CoreException result = assertThrows(CoreException.class, userCoupon::use);

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("EXPIRED 상태인 쿠폰을 사용하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpired() {
            UserCouponModel userCoupon = new UserCouponModel(1L, 1L, 10L, CouponStatus.EXPIRED, 0L, null, null);

            CoreException result = assertThrows(CoreException.class, userCoupon::use);

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 사용 가능 여부를 확인할 때, ")
    @Nested
    class IsUsable {

        @DisplayName("AVAILABLE 상태이고 만료일이 미래이면 true를 반환한다.")
        @Test
        void returnsTrue_whenAvailableAndNotExpired() {
            UserCouponModel userCoupon = new UserCouponModel(1L, 10L);
            ZonedDateTime futureExpiredAt = ZonedDateTime.now().plusDays(1);

            assertThat(userCoupon.isUsable(futureExpiredAt)).isTrue();
        }

        @DisplayName("AVAILABLE 상태이지만 만료일이 지났으면 false를 반환한다.")
        @Test
        void returnsFalse_whenAvailableButExpired() {
            UserCouponModel userCoupon = new UserCouponModel(1L, 10L);
            ZonedDateTime pastExpiredAt = ZonedDateTime.now().minusDays(1);

            assertThat(userCoupon.isUsable(pastExpiredAt)).isFalse();
        }

        @DisplayName("USED 상태이면 만료일과 무관하게 false를 반환한다.")
        @Test
        void returnsFalse_whenUsed() {
            UserCouponModel userCoupon = new UserCouponModel(1L, 1L, 10L, CouponStatus.USED, 0L, null, null);
            ZonedDateTime futureExpiredAt = ZonedDateTime.now().plusDays(1);

            assertThat(userCoupon.isUsable(futureExpiredAt)).isFalse();
        }
    }
}
