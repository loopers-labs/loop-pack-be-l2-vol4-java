package com.loopers.domain.coupon;

import com.loopers.domain.coupon.enums.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponUsePolicyTest {

    private final CouponUsePolicy policy = new CouponUsePolicy();

    private static final Long USER_ID = 1L;
    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    private UserCouponModel createUserCoupon(Long userId, ZonedDateTime expiredAt) {
        CouponModel coupon = new CouponModel(
                "테스트 쿠폰",
                new CouponDiscount(CouponType.RATE, 10L, null),
                new CouponExpiry(expiredAt)
        );
        return new UserCouponModel(userId, coupon);
    }

    @DisplayName("쿠폰 사용 검증 시,")
    @Nested
    class Validate {

        @DisplayName("모든 조건이 충족되면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenAllConditionsAreMet() {
            UserCouponModel userCoupon = createUserCoupon(USER_ID, FUTURE);

            assertDoesNotThrow(() -> policy.validate(userCoupon, USER_ID));
        }

        @DisplayName("요청자가 쿠폰 소유자가 아니면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsUserCouponNotOwned_whenRequesterIsNotOwner() {
            UserCouponModel userCoupon = createUserCoupon(USER_ID, FUTURE);

            CoreException exception = assertThrows(CoreException.class, () ->
                    policy.validate(userCoupon, 999L)
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("이미 사용된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsUserCouponAlreadyUsed_whenCouponIsAlreadyUsed() {
            UserCouponModel userCoupon = createUserCoupon(USER_ID, FUTURE);
            userCoupon.use();

            CoreException exception = assertThrows(CoreException.class, () ->
                    policy.validate(userCoupon, USER_ID)
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsUserCouponExpired_whenCouponIsExpired() {
            UserCouponModel userCoupon = createUserCoupon(USER_ID, PAST);

            CoreException exception = assertThrows(CoreException.class, () ->
                    policy.validate(userCoupon, USER_ID)
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
