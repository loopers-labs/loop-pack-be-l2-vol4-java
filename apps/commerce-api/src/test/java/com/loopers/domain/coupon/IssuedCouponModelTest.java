package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssuedCouponModelTest {

    @DisplayName("발급 쿠폰을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성하면, 상태가 AVAILABLE로 생성된다.")
        @Test
        void createsIssuedCoupon_withAvailableStatus_whenValidInfoIsProvided() {
            IssuedCoupon issuedCoupon = new IssuedCoupon(1L, 2L, ZonedDateTime.now().plusDays(30));

            assertAll(
                () -> assertThat(issuedCoupon.getCouponId()).isEqualTo(1L),
                () -> assertThat(issuedCoupon.getUserId()).isEqualTo(2L),
                () -> assertThat(issuedCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE),
                () -> assertThat(issuedCoupon.getUsedAt()).isNull()
            );
        }

        @DisplayName("couponId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIdIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new IssuedCoupon(null, 2L, ZonedDateTime.now().plusDays(30)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("userId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new IssuedCoupon(1L, null, ZonedDateTime.now().plusDays(30)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰을 사용할 때,")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태이면, USED로 변경되고 usedAt이 설정된다.")
        @Test
        void usesIssuedCoupon_whenStatusIsAvailable() {
            IssuedCoupon issuedCoupon = new IssuedCoupon(1L, 2L, ZonedDateTime.now().plusDays(30));

            issuedCoupon.use();

            assertAll(
                () -> assertThat(issuedCoupon.getStatus()).isEqualTo(CouponStatus.USED),
                () -> assertThat(issuedCoupon.getUsedAt()).isNotNull()
            );
        }

        @DisplayName("이미 USED 상태이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenStatusIsAlreadyUsed() {
            IssuedCoupon issuedCoupon = new IssuedCoupon(1L, 2L, ZonedDateTime.now().plusDays(30));
            issuedCoupon.use();

            CoreException ex = assertThrows(CoreException.class, issuedCoupon::use);
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("만료된 쿠폰이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenCouponIsExpired() {
            IssuedCoupon issuedCoupon = new IssuedCoupon(1L, 2L, ZonedDateTime.now().minusDays(1));

            CoreException ex = assertThrows(CoreException.class, issuedCoupon::use);
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("쿠폰 사용 가능 여부를 확인할 때,")
    @Nested
    class IsAvailable {

        @DisplayName("AVAILABLE 상태이고 만료 전이면, true를 반환한다.")
        @Test
        void returnsTrue_whenStatusIsAvailableAndNotExpired() {
            IssuedCoupon issuedCoupon = new IssuedCoupon(1L, 2L, ZonedDateTime.now().plusDays(30));

            assertThat(issuedCoupon.isAvailable()).isTrue();
        }

        @DisplayName("USED 상태이면, false를 반환한다.")
        @Test
        void returnsFalse_whenStatusIsUsed() {
            IssuedCoupon issuedCoupon = new IssuedCoupon(1L, 2L, ZonedDateTime.now().plusDays(30));
            issuedCoupon.use();

            assertThat(issuedCoupon.isAvailable()).isFalse();
        }

        @DisplayName("AVAILABLE 상태이지만 만료된 경우, false를 반환한다.")
        @Test
        void returnsFalse_whenStatusIsAvailableButExpired() {
            IssuedCoupon issuedCoupon = new IssuedCoupon(1L, 2L, ZonedDateTime.now().minusDays(1));

            assertThat(issuedCoupon.isAvailable()).isFalse();
        }
    }
}