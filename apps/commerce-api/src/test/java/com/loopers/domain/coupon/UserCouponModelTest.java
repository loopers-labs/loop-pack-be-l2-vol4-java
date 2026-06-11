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

class UserCouponModelTest {

    @DisplayName("쿠폰을 사용할 때")
    @Nested
    class Use {

        @DisplayName("사용 처리되고 사용 시각이 기록된다.")
        @Test
        void marksUsed() {
            UserCouponModel userCoupon = UserCouponModel.of(1L, 2L);
            ZonedDateTime now = ZonedDateTime.now();

            userCoupon.use(now);

            assertAll(
                    () -> assertThat(userCoupon.isUsed()).isTrue(),
                    () -> assertThat(userCoupon.getUsedAt()).isEqualTo(now)
            );
        }

        @DisplayName("이미 사용된 쿠폰을 다시 사용하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyUsed() {
            UserCouponModel userCoupon = UserCouponModel.of(1L, 2L);
            userCoupon.use(ZonedDateTime.now());

            CoreException result = assertThrows(CoreException.class,
                    () -> userCoupon.use(ZonedDateTime.now()));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("쿠폰 사용을 취소하면")
    @Nested
    class Cancel {

        @DisplayName("미사용 상태로 돌아가고 사용 시각이 비워진다.")
        @Test
        void resetsToUnused() {
            UserCouponModel userCoupon = UserCouponModel.of(1L, 2L);
            userCoupon.use(ZonedDateTime.now());

            userCoupon.cancel();

            assertAll(
                    () -> assertThat(userCoupon.isUsed()).isFalse(),
                    () -> assertThat(userCoupon.getUsedAt()).isNull()
            );
        }
    }
}