package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponIssueRequestModelTest {

    private static final Long USER_ID = 1L;
    private static final Long COUPON_ID = 10L;

    @DisplayName("CouponIssueRequestModel 생성 시,")
    @Nested
    class Create {

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponIssueRequestModel(null, COUPON_ID));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("couponId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponIssueRequestModel(USER_ID, null));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정상 생성 시 PENDING 상태로 생성된다.")
        @Test
        void createsWithPendingStatus() {
            CouponIssueRequestModel request = new CouponIssueRequestModel(USER_ID, COUPON_ID);

            assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.PENDING);
            assertThat(request.getFailureReason()).isNull();
            assertThat(request.getUserCouponId()).isNull();
        }
    }

    @DisplayName("markIssued()를 호출하면,")
    @Nested
    class MarkIssued {

        @DisplayName("상태가 ISSUED로 바뀌고 userCouponId가 저장된다.")
        @Test
        void transitionsToIssued() {
            CouponIssueRequestModel request = new CouponIssueRequestModel(USER_ID, COUPON_ID);

            request.markIssued(99L);

            assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED);
            assertThat(request.getUserCouponId()).isEqualTo(99L);
        }
    }

    @DisplayName("markFailed()를 호출하면,")
    @Nested
    class MarkFailed {

        @DisplayName("상태가 FAILED로 바뀌고 실패 사유가 저장된다.")
        @Test
        void transitionsToFailed() {
            CouponIssueRequestModel request = new CouponIssueRequestModel(USER_ID, COUPON_ID);

            request.markFailed("쿠폰이 모두 소진되었습니다.");

            assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.FAILED);
            assertThat(request.getFailureReason()).isEqualTo("쿠폰이 모두 소진되었습니다.");
        }
    }
}
