package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserCouponTest {

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    class Issue {

        @DisplayName("유효한 유저/쿠폰 ID 로 발급하면 AVAILABLE 상태로 생성된다.")
        @Test
        void createsAsAvailable() {
            UserCoupon uc = UserCoupon.issue(1L, 100L);

            assertThat(uc.getUserId()).isEqualTo(1L);
            assertThat(uc.getCouponId()).isEqualTo(100L);
            assertThat(uc.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
            assertThat(uc.getUsedAt()).isNull();
            assertThat(uc.getIssuedAt()).isNotNull();
        }

        @DisplayName("userId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> UserCoupon.issue(null, 100L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰을 사용할 때, ")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태이면 USED 로 전이되고 usedAt 이 기록된다.")
        @Test
        void transitionsToUsed_whenAvailable() {
            UserCoupon uc = UserCoupon.issue(1L, 100L);

            uc.use();

            assertThat(uc.getStatus()).isEqualTo(CouponStatus.USED);
            assertThat(uc.getUsedAt()).isNotNull();
        }

        @DisplayName("이미 USED 인 쿠폰을 다시 사용하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyUsed() {
            UserCoupon uc = UserCoupon.issue(1L, 100L);
            uc.use();

            CoreException result = assertThrows(CoreException.class, uc::use);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("소유권을 검증할 때, ")
    @Nested
    class BelongsTo {

        @DisplayName("쿠폰 소유자 ID 와 일치하면 true 를 반환한다.")
        @Test
        void returnsTrue_whenSameUser() {
            UserCoupon uc = UserCoupon.issue(1L, 100L);
            assertThat(uc.belongsTo(1L)).isTrue();
            assertThat(uc.belongsTo(2L)).isFalse();
        }
    }
}
