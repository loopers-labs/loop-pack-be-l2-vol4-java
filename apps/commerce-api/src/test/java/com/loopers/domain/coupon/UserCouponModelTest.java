package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserCouponModelTest {

    private static final Long USER_ID = 1L;
    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    private CouponModel activeCoupon() {
        return new CouponModel("10% 할인", CouponType.RATE, 10, null, FUTURE);
    }

    private CouponModel expiredCoupon() {
        return new CouponModel("만료쿠폰", CouponType.RATE, 10, null, PAST);
    }

    @DisplayName("UserCouponModel 생성 시,")
    @Nested
    class Create {

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new UserCouponModel(null, activeCoupon()));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("coupon이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new UserCouponModel(USER_ID, null));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 인자로 생성 시 status가 AVAILABLE로 초기화된다.")
        @Test
        void initializesWithAvailableStatus() {
            UserCouponModel userCoupon = new UserCouponModel(USER_ID, activeCoupon());

            assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
        }
    }

    @DisplayName("use()를 호출할 때,")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태의 쿠폰 사용 시 status가 USED로 변경된다.")
        @Test
        void changesStatusToUsed_whenAvailable() {
            UserCouponModel userCoupon = new UserCouponModel(USER_ID, activeCoupon());

            userCoupon.use();

            assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
        }

        @DisplayName("이미 사용된 쿠폰으로 use() 호출 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyUsed() {
            UserCouponModel userCoupon = new UserCouponModel(USER_ID, activeCoupon());
            userCoupon.use();

            CoreException result = assertThrows(CoreException.class, userCoupon::use);

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료된 쿠폰으로 use() 호출 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIsExpired() {
            UserCouponModel userCoupon = new UserCouponModel(USER_ID, expiredCoupon());

            CoreException result = assertThrows(CoreException.class, userCoupon::use);

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("computedStatus()를 호출할 때,")
    @Nested
    class ComputedStatus {

        @DisplayName("유효한 쿠폰이고 미사용이면 AVAILABLE을 반환한다.")
        @Test
        void returnsAvailable_whenActiveAndUnused() {
            UserCouponModel userCoupon = new UserCouponModel(USER_ID, activeCoupon());

            assertThat(userCoupon.computedStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
        }

        @DisplayName("사용된 쿠폰이면 USED를 반환한다.")
        @Test
        void returnsUsed_whenAlreadyUsed() {
            UserCouponModel userCoupon = new UserCouponModel(USER_ID, activeCoupon());
            userCoupon.use();

            assertThat(userCoupon.computedStatus()).isEqualTo(UserCouponStatus.USED);
        }

        @DisplayName("미사용이지만 쿠폰이 만료됐으면 EXPIRED를 반환한다.")
        @Test
        void returnsExpired_whenCouponIsExpiredAndUnused() {
            UserCouponModel userCoupon = new UserCouponModel(USER_ID, expiredCoupon());

            assertThat(userCoupon.computedStatus()).isEqualTo(UserCouponStatus.EXPIRED);
        }
    }
}
