package com.loopers.domain.coupon;

import com.loopers.domain.coupon.enums.UserCouponStatus;
import com.loopers.domain.coupon.enums.CouponType;
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

    private UserCouponModel createUserCoupon() {
        CouponModel coupon = new CouponModel(
                "테스트 쿠폰",
                new CouponDiscount(CouponType.RATE, 10L, null),
                new CouponExpiry(FUTURE)
        );
        return new UserCouponModel(USER_ID, coupon);
    }

    @DisplayName("유저 쿠폰 생성 시,")
    @Nested
    class Create {

        @DisplayName("유저 ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CouponModel coupon = new CouponModel(
                    "테스트 쿠폰",
                    new CouponDiscount(CouponType.RATE, 10L, null),
                    new CouponExpiry(FUTURE)
            );

            CoreException exception = assertThrows(CoreException.class, () -> new UserCouponModel(null, coupon));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("쿠폰이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIsNull() {
            CoreException exception = assertThrows(CoreException.class, () -> new UserCouponModel(USER_ID, null));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정상 생성 시, 초기 상태가 ISSUED이다.")
        @Test
        void hasIssuedStatus_whenCreatedSuccessfully() {
            UserCouponModel userCoupon = createUserCoupon();

            assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        }
    }

    @DisplayName("쿠폰 사용 처리 시,")
    @Nested
    class Use {

        @DisplayName("ISSUED 상태이면, 상태가 USED로 변경된다.")
        @Test
        void changesStatusToUsed_whenStatusIsIssued() {
            UserCouponModel userCoupon = createUserCoupon();

            userCoupon.use();

            assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
        }

        @DisplayName("ISSUED 상태이면, usedAt이 설정된다.")
        @Test
        void setsUsedAt_whenStatusIsIssued() {
            UserCouponModel userCoupon = createUserCoupon();

            userCoupon.use();

            assertThat(userCoupon.getUsedAt()).isNotNull();
        }

        @DisplayName("이미 사용된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsUserCouponAlreadyUsed_whenAlreadyUsed() {
            UserCouponModel userCoupon = createUserCoupon();
            userCoupon.use();

            CoreException exception = assertThrows(CoreException.class, userCoupon::use);

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
