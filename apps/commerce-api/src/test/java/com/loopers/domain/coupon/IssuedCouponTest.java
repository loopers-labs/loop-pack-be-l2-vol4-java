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

class IssuedCouponTest {

    @DisplayName("발급 쿠폰을 사용할 때, ")
    @Nested
    class Use {
        @DisplayName("사용 가능한 본인 쿠폰이면, USED 상태로 변경한다.")
        @Test
        void marksUsed_whenCouponIsAvailableAndOwnedByUser() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            IssuedCoupon issuedCoupon = new IssuedCoupon(1L, "user1234", now.plusDays(7));

            // act
            issuedCoupon.use("user1234", now);

            // assert
            assertAll(
                () -> assertThat(issuedCoupon.currentStatus(now)).isEqualTo(CouponStatus.USED),
                () -> assertThat(issuedCoupon.getUsedAt()).isEqualTo(now)
            );
        }

        @DisplayName("다른 회원의 쿠폰이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenCouponIsOwnedByAnotherUser() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            IssuedCoupon issuedCoupon = new IssuedCoupon(1L, "user1234", now.plusDays(7));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                issuedCoupon.use("user5678", now);
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(issuedCoupon.currentStatus(now)).isEqualTo(CouponStatus.AVAILABLE)
            );
        }

        @DisplayName("만료된 쿠폰이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenCouponIsExpired() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            IssuedCoupon issuedCoupon = new IssuedCoupon(1L, "user1234", now.minusDays(1));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                issuedCoupon.use("user1234", now);
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(issuedCoupon.currentStatus(now)).isEqualTo(CouponStatus.EXPIRED)
            );
        }

        @DisplayName("이미 사용된 쿠폰이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenCouponIsAlreadyUsed() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            IssuedCoupon issuedCoupon = new IssuedCoupon(1L, "user1234", now.plusDays(7));
            issuedCoupon.use("user1234", now);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                issuedCoupon.use("user1234", now.plusMinutes(1));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
