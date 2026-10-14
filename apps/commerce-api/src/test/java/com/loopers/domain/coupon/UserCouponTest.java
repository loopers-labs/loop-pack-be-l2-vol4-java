package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserCouponTest {

    private static UserCoupon newCoupon() {
        return new UserCoupon(100L, 1L, LocalDateTime.now());
    }

    @DisplayName("쿠폰 발급 시, 상태는 AVAILABLE 이고 usedAt 은 null 이다.")
    @Test
    void issuedState() {
        // act
        UserCoupon coupon = newCoupon();

        // assert
        assertAll(
            () -> assertThat(coupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE),
            () -> assertThat(coupon.getUsedAt()).isNull()
        );
    }

    @DisplayName("쿠폰을 사용할 때, ")
    @Nested
    class Use {
        @DisplayName("AVAILABLE 상태에서 사용하면, USED 로 전이되고 usedAt 이 설정된다.")
        @Test
        void usesAvailable() {
            // arrange
            UserCoupon coupon = newCoupon();
            LocalDateTime now = LocalDateTime.now();

            // act
            coupon.use(now);

            // assert
            assertAll(
                () -> assertThat(coupon.getStatus()).isEqualTo(UserCouponStatus.USED),
                () -> assertThat(coupon.getUsedAt()).isEqualTo(now)
            );
        }

        @DisplayName("USED 상태에서 다시 사용하면, CONFLICT 예외가 발생한다 — 재사용 불가.")
        @Test
        void throwsConflict_whenAlreadyUsed() {
            // arrange
            UserCoupon coupon = newCoupon();
            coupon.use(LocalDateTime.now());

            // act
            CoreException result = assertThrows(CoreException.class, () -> coupon.use(LocalDateTime.now()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("EXPIRED 상태에서 사용하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenExpired() {
            // arrange
            UserCoupon coupon = newCoupon();
            coupon.expire();

            // act
            CoreException result = assertThrows(CoreException.class, () -> coupon.use(LocalDateTime.now()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("소유자 검증 시, ")
    @Nested
    class Ownership {
        @DisplayName("소유자와 일치하면 통과한다.")
        @Test
        void passesWhenOwned() {
            // arrange
            UserCoupon coupon = newCoupon();

            // act & assert
            coupon.assertOwnedBy(100L);
        }

        @DisplayName("타 유저의 쿠폰이면 NOT_FOUND 예외가 발생한다 — 존재 자체를 숨김.")
        @Test
        void throwsNotFound_whenOtherUser() {
            // arrange
            UserCoupon coupon = newCoupon();

            // act
            CoreException result = assertThrows(CoreException.class, () -> coupon.assertOwnedBy(999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
