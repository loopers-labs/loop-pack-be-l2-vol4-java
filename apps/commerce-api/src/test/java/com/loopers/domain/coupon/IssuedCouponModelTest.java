package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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

}
