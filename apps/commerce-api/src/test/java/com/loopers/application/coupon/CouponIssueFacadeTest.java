package com.loopers.application.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.event.CouponIssueRequestedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CouponIssueFacadeTest {

    private CouponIssueFacade facade;

    @Mock private CouponRepository couponRepository;
    @Mock private CouponIssueRequestRepository couponIssueRequestRepository;
    @Mock private UserCouponService userCouponService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long USER_ID = 1L;
    private static final Long COUPON_ID = 10L;
    private static final Long REQUEST_ID = 100L;

    @BeforeEach
    void setUp() {
        facade = new CouponIssueFacade(
            couponRepository, couponIssueRequestRepository, userCouponService, eventPublisher, new ObjectMapper());
    }

    private CouponModel activeCoupon() {
        return new CouponModel("10% 할인", CouponType.RATE, 10, null, ZonedDateTime.now().plusDays(30), null);
    }

    private CouponIssueRequestModel pendingRequest() {
        CouponIssueRequestModel request = new CouponIssueRequestModel(USER_ID, COUPON_ID);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        return request;
    }

    @DisplayName("requestIssue()를 호출할 때,")
    @Nested
    class RequestIssue {

        @DisplayName("쿠폰이 존재하면 PENDING 요청이 저장되고 CouponIssueRequestedEvent가 발행된다.")
        @Test
        void savesRequestAndPublishesEvent_whenCouponExists() {
            // arrange
            given(couponRepository.findById(COUPON_ID)).willReturn(Optional.of(activeCoupon()));
            given(couponIssueRequestRepository.save(any())).willAnswer(inv -> {
                CouponIssueRequestModel saved = inv.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", REQUEST_ID);
                return saved;
            });

            // act
            CouponIssueRequestInfo result = facade.requestIssue(USER_ID, COUPON_ID);

            // assert
            assertThat(result.status()).isEqualTo(CouponIssueRequestStatus.PENDING);
            then(eventPublisher).should().publishEvent(new CouponIssueRequestedEvent(REQUEST_ID, USER_ID, COUPON_ID));
        }

        @DisplayName("존재하지 않는 쿠폰이면 NOT_FOUND 예외가 발생하고 요청이 저장되지 않는다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            // arrange
            given(couponRepository.findById(COUPON_ID)).willReturn(Optional.empty());

            // act & assert
            CoreException result = assertThrows(CoreException.class, () -> facade.requestIssue(USER_ID, COUPON_ID));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            then(couponIssueRequestRepository).should(never()).save(any());
            then(eventPublisher).should(never()).publishEvent(any());
        }

        @DisplayName("소프트딜리트된 쿠폰이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponIsDeleted() {
            // arrange
            CouponModel deleted = activeCoupon();
            deleted.delete();
            given(couponRepository.findById(COUPON_ID)).willReturn(Optional.of(deleted));

            // act & assert
            CoreException result = assertThrows(CoreException.class, () -> facade.requestIssue(USER_ID, COUPON_ID));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("getRequest()를 호출할 때,")
    @Nested
    class GetRequest {

        @DisplayName("본인 요청이면 CouponIssueRequestInfo가 반환된다.")
        @Test
        void returnsInfo_whenRequestBelongsToUser() {
            // arrange
            given(couponIssueRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(pendingRequest()));

            // act
            CouponIssueRequestInfo result = facade.getRequest(REQUEST_ID, USER_ID);

            // assert
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.couponId()).isEqualTo(COUPON_ID);
        }

        @DisplayName("존재하지 않는 요청이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenRequestDoesNotExist() {
            given(couponIssueRequestRepository.findById(REQUEST_ID)).willReturn(Optional.empty());

            CoreException result = assertThrows(CoreException.class, () -> facade.getRequest(REQUEST_ID, USER_ID));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("타인의 요청이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenRequestBelongsToOtherUser() {
            given(couponIssueRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(pendingRequest()));

            CoreException result = assertThrows(CoreException.class, () -> facade.getRequest(REQUEST_ID, 999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("handle()을 호출할 때,")
    @Nested
    class Handle {

        private String toJson(Long requestId, Long userId, Long couponId) {
            try {
                return new ObjectMapper().writeValueAsString(
                    new java.util.HashMap<>() {{
                        put("eventId", "event-1");
                        put("eventType", "ISSUE_REQUESTED");
                        put("requestId", requestId);
                        put("userId", userId);
                        put("couponId", couponId);
                    }});
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @DisplayName("정상 발급되면 요청이 ISSUED로 갱신되고 userCouponId가 저장된다.")
        @Test
        void marksIssued_whenIssueSucceeds() {
            // arrange
            CouponIssueRequestModel request = pendingRequest();
            given(couponIssueRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(request));
            UserCouponInfo issued = new UserCouponInfo(555L, USER_ID, null, null, null);
            given(userCouponService.issue(USER_ID, COUPON_ID)).willReturn(issued);

            // act
            facade.handle(toJson(REQUEST_ID, USER_ID, COUPON_ID));

            // assert
            assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED);
            assertThat(request.getUserCouponId()).isEqualTo(555L);
        }

        @DisplayName("비즈니스 규칙 위반(CoreException)이면 요청이 FAILED로 갱신되고 예외는 전파되지 않는다.")
        @Test
        void marksFailed_whenIssueThrowsCoreException() {
            // arrange
            CouponIssueRequestModel request = pendingRequest();
            given(couponIssueRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(request));
            willThrow(new CoreException(ErrorType.CONFLICT, "쿠폰이 모두 소진되었습니다."))
                .given(userCouponService).issue(USER_ID, COUPON_ID);

            // act & assert
            assertDoesNotThrow(() -> facade.handle(toJson(REQUEST_ID, USER_ID, COUPON_ID)));
            assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.FAILED);
            assertThat(request.getFailureReason()).isEqualTo("쿠폰이 모두 소진되었습니다.");
        }

        @DisplayName("이미 처리된(PENDING이 아닌) 요청이면 재발급을 시도하지 않는다.")
        @Test
        void skipsProcessing_whenRequestAlreadyProcessed() {
            // arrange
            CouponIssueRequestModel request = pendingRequest();
            request.markIssued(555L);
            given(couponIssueRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(request));

            // act
            facade.handle(toJson(REQUEST_ID, USER_ID, COUPON_ID));

            // assert
            then(userCouponService).should(never()).issue(any(), any());
        }

        @DisplayName("존재하지 않는 요청 ID면 예외가 전파된다.")
        @Test
        void propagatesException_whenRequestDoesNotExist() {
            given(couponIssueRequestRepository.findById(REQUEST_ID)).willReturn(Optional.empty());

            CoreException result = assertThrows(CoreException.class,
                () -> facade.handle(toJson(REQUEST_ID, USER_ID, COUPON_ID)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("파싱할 수 없는 페이로드면 예외가 전파된다.")
        @Test
        void propagatesException_whenPayloadIsMalformed() {
            CoreException result = assertThrows(CoreException.class, () -> facade.handle("not-a-json"));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        }
    }
}
