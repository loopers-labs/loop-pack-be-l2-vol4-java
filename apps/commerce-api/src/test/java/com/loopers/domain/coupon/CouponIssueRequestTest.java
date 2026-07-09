package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponIssueRequestTest {

    private static CouponIssueRequest pending() {
        return CouponIssueRequest.pending("req-1", 100L, 10L);
    }

    @DisplayName("pending 으로 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상 값이면 PENDING 상태로 생성된다.")
        @Test
        void createsPending_whenValid() {
            CouponIssueRequest request = pending();

            assertThat(request.getRequestId()).isEqualTo("req-1");
            assertThat(request.getUserId()).isEqualTo(100L);
            assertThat(request.getTemplateId()).isEqualTo(10L);
            assertThat(request.getStatus()).isEqualTo(CouponIssueStatus.PENDING);
            assertThat(request.isPending()).isTrue();
        }

        @DisplayName("requestId 가 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void throwsBadRequest_whenRequestIdBlank(String requestId) {
            CoreException result = assertThrows(CoreException.class,
                    () -> CouponIssueRequest.pending(requestId, 100L, 10L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("userId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> CouponIssueRequest.pending("req-1", null, 10L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("templateId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTemplateIdNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> CouponIssueRequest.pending("req-1", 100L, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상태 전이는 ")
    @Nested
    class Transition {

        @DisplayName("markSuccess 는 SUCCESS 로 전이한다.")
        @Test
        void markSuccess_transitions() {
            CouponIssueRequest request = pending();
            request.markSuccess();
            assertThat(request.getStatus()).isEqualTo(CouponIssueStatus.SUCCESS);
            assertThat(request.isPending()).isFalse();
        }

        @DisplayName("markSoldOut 은 SOLD_OUT 으로 전이한다.")
        @Test
        void markSoldOut_transitions() {
            CouponIssueRequest request = pending();
            request.markSoldOut();
            assertThat(request.getStatus()).isEqualTo(CouponIssueStatus.SOLD_OUT);
        }

        @DisplayName("markAlreadyIssued 은 ALREADY_ISSUED 로 전이한다.")
        @Test
        void markAlreadyIssued_transitions() {
            CouponIssueRequest request = pending();
            request.markAlreadyIssued();
            assertThat(request.getStatus()).isEqualTo(CouponIssueStatus.ALREADY_ISSUED);
        }

        @DisplayName("markFailed 은 FAILED 로 전이한다.")
        @Test
        void markFailed_transitions() {
            CouponIssueRequest request = pending();
            request.markFailed();
            assertThat(request.getStatus()).isEqualTo(CouponIssueStatus.FAILED);
        }
    }

    @DisplayName("터미널 불변식 — 이미 처리된 요청은 재전이를 거부한다(이중발급 방지). ")
    @Nested
    class TerminalImmutability {

        @DisplayName("이미 SUCCESS 된 요청을 다시 전이하려 하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenReTransitioningSuccess() {
            CouponIssueRequest request = pending();
            request.markSuccess();

            CoreException result = assertThrows(CoreException.class, request::markSoldOut);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(request.getStatus()).isEqualTo(CouponIssueStatus.SUCCESS);
        }

        @DisplayName("SOLD_OUT 된 요청을 다시 전이하려 하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTransitioningTerminal() {
            CouponIssueRequest request = pending();
            request.markSoldOut();

            CoreException result = assertThrows(CoreException.class, request::markSuccess);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(request.getStatus()).isEqualTo(CouponIssueStatus.SOLD_OUT);
        }
    }
}
