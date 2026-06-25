package com.loopers.domain.coupon;

import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserCouponTest {

    private static final ZonedDateTime NOW = ZonedDateTime.now();
    private static final Long OWNER = 1L;

    private UserCoupon coupon() {
        CouponSnapshot snapshot = new CouponSnapshot("5천원 할인", CouponType.FIXED, 5000L, null);
        return new UserCoupon(OWNER, 10L, snapshot, NOW, NOW.plusDays(30));
    }

    @DisplayName("사용 시, ")
    @Nested
    class Use {

        @DisplayName("정상 사용하면 USED 상태가 되고 사용 시각이 기록된다.")
        @Test
        void uses() {
            // arrange
            UserCoupon userCoupon = coupon();

            // act
            userCoupon.use(OWNER, NOW);

            // assert
            assertAll(
                () -> assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED),
                () -> assertThat(userCoupon.getUsedAt()).isEqualTo(NOW)
            );
        }

        @DisplayName("소유자가 아니면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throws_whenNotOwner() {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> coupon().use(999L, NOW));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("이미 사용된 쿠폰이면 예외가 발생한다.")
        @Test
        void throws_whenAlreadyUsed() {
            // arrange
            UserCoupon userCoupon = coupon();
            userCoupon.use(OWNER, NOW);

            // act & assert
            assertThrows(CoreException.class, () -> userCoupon.use(OWNER, NOW));
        }

        @DisplayName("만료된 쿠폰이면 예외가 발생한다.")
        @Test
        void throws_whenExpired() {
            // act & assert
            assertThrows(CoreException.class, () -> coupon().use(OWNER, NOW.plusDays(31)));
        }
    }

    @DisplayName("할인 계산은 스냅샷에 위임한다.")
    @Test
    void delegatesDiscountToSnapshot() {
        // act & assert
        assertThat(coupon().calculateDiscount(Money.of(20000L))).isEqualTo(Money.of(5000L));
    }

    @DisplayName("파생 상태: 미사용+만료면 EXPIRED, 사용됨이면 USED, 그 외 AVAILABLE.")
    @Test
    void derivesStatus() {
        // arrange
        UserCoupon available = coupon();
        UserCoupon used = coupon();
        used.use(OWNER, NOW);

        // act & assert
        assertAll(
            () -> assertThat(available.statusFor(NOW)).isEqualTo(UserCouponStatus.AVAILABLE),
            () -> assertThat(available.statusFor(NOW.plusDays(31))).isEqualTo(UserCouponStatus.EXPIRED),
            () -> assertThat(used.statusFor(NOW.plusDays(31))).isEqualTo(UserCouponStatus.USED)
        );
    }

    @DisplayName("결제 실패로 복원 시, ")
    @Nested
    class Restore {

        @DisplayName("USED 쿠폰을 복원하면 다시 AVAILABLE 이 된다.")
        @Test
        void restore_fromUsed() {
            // arrange
            UserCoupon userCoupon = coupon();
            userCoupon.use(OWNER, NOW);

            // act
            userCoupon.restore();

            // assert
            assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
        }

        @DisplayName("이미 AVAILABLE 이면 멱등하게 AVAILABLE 을 유지한다.")
        @Test
        void restore_isIdempotent() {
            // arrange
            UserCoupon userCoupon = coupon();

            // act
            userCoupon.restore();

            // assert
            assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
        }
    }
}
