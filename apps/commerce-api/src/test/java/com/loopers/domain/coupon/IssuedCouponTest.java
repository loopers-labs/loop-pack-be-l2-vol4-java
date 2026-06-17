package com.loopers.domain.coupon;

import com.loopers.domain.coupon.model.CouponStatus;
import com.loopers.domain.coupon.model.IssuedCoupon;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssuedCouponTest {

    @DisplayName("발급 쿠폰 생성 시, ")
    @Nested
    class Create {

        @DisplayName("정상 생성하면 AVAILABLE 상태다.")
        @Test
        void createsWithAvailableStatus() {
            IssuedCoupon issued = IssuedCoupon.create(1L, 100L);
            assertThat(issued.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("memberId가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMemberIdIsNull() {
            CoreException ex = assertThrows(CoreException.class, () ->
                IssuedCoupon.create(null, 100L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("couponTemplateId가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponTemplateIdIsNull() {
            CoreException ex = assertThrows(CoreException.class, () ->
                IssuedCoupon.create(1L, null)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 사용 처리 시, ")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태의 쿠폰을 사용하면 USED 상태가 된다.")
        @Test
        void usesAvailableCoupon() {
            IssuedCoupon issued = IssuedCoupon.create(1L, 100L);
            issued.use();
            assertThat(issued.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @DisplayName("이미 USED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyUsed() {
            IssuedCoupon issued = IssuedCoupon.create(1L, 100L);
            issued.use();

            CoreException ex = assertThrows(CoreException.class, issued::use);
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("EXPIRED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpired() {
            IssuedCoupon issued = IssuedCoupon.create(1L, 100L);
            issued.expire();

            CoreException ex = assertThrows(CoreException.class, issued::use);
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
