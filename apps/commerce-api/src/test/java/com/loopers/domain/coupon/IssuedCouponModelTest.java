package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssuedCouponModelTest {

    private IssuedCouponModel issuedCoupon() {
        return new IssuedCouponModel(1L, 100L);
    }

    @DisplayName("발급 쿠폰을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 입력이면 상태가 AVAILABLE로 설정된다.")
        @Test
        void issuedCouponIsCreatedWithAvailableStatus_whenValidInputIsProvided() {
            // when
            IssuedCouponModel coupon = new IssuedCouponModel(1L, 100L);

            // then
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("쿠폰 템플릿 ID가 없으면 발급 쿠폰을 생성할 수 없다.")
        @Test
        void issuedCouponCannotBeCreated_whenCouponTemplateIdIsNull() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new IssuedCouponModel(null, 100L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("사용자 ID가 없으면 발급 쿠폰을 생성할 수 없다.")
        @Test
        void issuedCouponCannotBeCreated_whenUserIdIsNull() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new IssuedCouponModel(1L, null));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("소유자를 검증할 때,")
    @Nested
    class ValidateOwner {

        @DisplayName("쿠폰 소유자이면 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenUserIsOwner() {
            // given
            IssuedCouponModel coupon = issuedCoupon();

            // when & then
            assertDoesNotThrow(() -> coupon.validateOwner(100L));
        }

        @DisplayName("다른 사용자이면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenUserIsNotOwner() {
            // given
            IssuedCouponModel coupon = issuedCoupon();

            // when
            CoreException result = assertThrows(CoreException.class, () -> coupon.validateOwner(999L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }

    @DisplayName("쿠폰을 사용할 때,")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태이면 USED 상태로 변경된다.")
        @Test
        void changesStatusToUsed_whenStatusIsAvailable() {
            // given
            IssuedCouponModel coupon = issuedCoupon();

            // when
            coupon.use();

            // then
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @DisplayName("이미 사용된 쿠폰이면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyUsed() {
            // given
            IssuedCouponModel coupon = issuedCoupon();
            coupon.use();

            // when
            CoreException result = assertThrows(CoreException.class, coupon::use);

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

}
