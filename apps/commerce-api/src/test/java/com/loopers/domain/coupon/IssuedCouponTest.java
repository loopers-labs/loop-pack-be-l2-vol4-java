package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssuedCouponTest {

    private static final Long USER_ID = 1L;
    private static final Long TEMPLATE_ID = 100L;

    @DisplayName("발급 쿠폰 생성 시")
    @Nested
    class Create {

        @DisplayName("유효한 값을 입력하면 AVAILABLE 상태로 생성된다")
        @Test
        void createsAsAvailable_whenAllFieldsAreValid() {
            // when
            IssuedCoupon coupon = new IssuedCoupon(USER_ID, TEMPLATE_ID);

            // then
            assertAll(
                () -> assertThat(coupon.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(coupon.getCouponTemplateId()).isEqualTo(TEMPLATE_ID),
                () -> assertThat(coupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException ex = assertThrows(CoreException.class, () -> new IssuedCoupon(null, TEMPLATE_ID));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("couponTemplateId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenTemplateIdIsNull() {
            CoreException ex = assertThrows(CoreException.class, () -> new IssuedCoupon(USER_ID, null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 사용 시")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태이면 USED로 전이된다")
        @Test
        void transitionsToUsed_whenAvailable() {
            // given
            IssuedCoupon coupon = new IssuedCoupon(USER_ID, TEMPLATE_ID);

            // when
            coupon.use();

            // then
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @DisplayName("이미 사용된 쿠폰을 다시 사용하면 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflict_whenAlreadyUsed() {
            // given
            IssuedCoupon coupon = new IssuedCoupon(USER_ID, TEMPLATE_ID);
            coupon.use();

            // when
            CoreException ex = assertThrows(CoreException.class, coupon::use);

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("소유자 확인 시")
    @Nested
    class IsOwnedBy {

        @DisplayName("발급받은 유저이면 true를 반환한다")
        @Test
        void returnsTrue_whenOwner() {
            IssuedCoupon coupon = new IssuedCoupon(USER_ID, TEMPLATE_ID);
            assertThat(coupon.isOwnedBy(USER_ID)).isTrue();
        }

        @DisplayName("다른 유저이면 false를 반환한다")
        @Test
        void returnsFalse_whenNotOwner() {
            IssuedCoupon coupon = new IssuedCoupon(USER_ID, TEMPLATE_ID);
            assertThat(coupon.isOwnedBy(999L)).isFalse();
        }
    }
}
