package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponIssueRequestTest {

    @DisplayName("발급 요청을 접수할 때, ")
    @Nested
    class Pending {

        @DisplayName("접수하면 PENDING 상태로 생성되고 유저·정책 ID 가 보관된다.")
        @Test
        void createsPendingRequest() {
            // given
            Long userId = 1L;
            Long couponPolicyId = 10L;

            // when
            CouponIssueRequest request = CouponIssueRequest.pending(userId, couponPolicyId);

            // then
            assertAll(
                () -> assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.PENDING),
                () -> assertThat(request.getUserId()).isEqualTo(userId),
                () -> assertThat(request.getCouponPolicyId()).isEqualTo(couponPolicyId)
            );
        }

        @DisplayName("유저 ID 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            // when
            CoreException result = assertThrows(CoreException.class,
                () -> CouponIssueRequest.pending(null, 10L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("발급 요청의 처리 결과를 확정할 때, ")
    @Nested
    class Finalize {

        @DisplayName("PENDING 요청을 발급 성공으로 확정하면, ISSUED 가 된다.")
        @Test
        void marksIssued_fromPending() {
            // given
            CouponIssueRequest request = CouponIssueRequest.pending(1L, 10L);

            // when
            request.markIssued();

            // then
            assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED);
        }

        @DisplayName("PENDING 요청을 거절로 확정하면, REJECTED 가 된다.")
        @Test
        void marksRejected_fromPending() {
            // given
            CouponIssueRequest request = CouponIssueRequest.pending(1L, 10L);

            // when
            request.markRejected();

            // then
            assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.REJECTED);
        }

        @DisplayName("이미 ISSUED 로 확정된 요청을 다시 확정하려 하면, COUPON_ISSUE_REQUEST_ALREADY_HANDLED 예외가 발생한다.")
        @Test
        void throwsAlreadyHandled_whenIssuedAgain() {
            // given
            CouponIssueRequest request = CouponIssueRequest.pending(1L, 10L);
            request.markIssued();

            // when
            CoreException result = assertThrows(CoreException.class, request::markRejected);

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_ISSUE_REQUEST_ALREADY_HANDLED);
        }

        @DisplayName("이미 REJECTED 로 확정된 요청을 다시 확정하려 하면, COUPON_ISSUE_REQUEST_ALREADY_HANDLED 예외가 발생한다.")
        @Test
        void throwsAlreadyHandled_whenRejectedAgain() {
            // given
            CouponIssueRequest request = CouponIssueRequest.pending(1L, 10L);
            request.markRejected();

            // when
            CoreException result = assertThrows(CoreException.class, request::markIssued);

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_ISSUE_REQUEST_ALREADY_HANDLED);
        }
    }
}
