package com.loopers.application.coupon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.event.CouponIssueRequestedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka 기반 비동기 쿠폰 발급 — API는 요청만 접수하고(Kafka 발행), 실제 발급은 Consumer(handle)가 처리한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueFacade {

    private final CouponRepository couponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final UserCouponService userCouponService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /** 발급 요청 접수 — 쿠폰 존재 여부만 빠르게 확인하고 나머지 검증(만료/중복/수량)은 Consumer가 최종 처리한다. */
    @Transactional
    public CouponIssueRequestInfo requestIssue(Long userId, Long couponId) {
        couponRepository.findById(couponId)
            .filter(c -> !c.isDeleted())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다."));

        CouponIssueRequestModel request = couponIssueRequestRepository.save(new CouponIssueRequestModel(userId, couponId));
        eventPublisher.publishEvent(new CouponIssueRequestedEvent(request.getId(), userId, couponId));
        return CouponIssueRequestInfo.from(request);
    }

    @Transactional(readOnly = true)
    public CouponIssueRequestInfo getRequest(Long requestId, Long requestUserId) {
        CouponIssueRequestModel request = couponIssueRequestRepository.findById(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
        if (!request.getUserId().equals(requestUserId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다.");
        }
        return CouponIssueRequestInfo.from(request);
    }

    /** Kafka Consumer 전용 — 실제 발급 처리. 비즈니스 규칙 위반(CoreException)은 요청을 FAILED로 남기고 정상 종료(ack)한다. */
    @Transactional
    public void handle(String rawPayload) {
        CouponIssuePayload payload = parse(rawPayload);

        CouponIssueRequestModel request = couponIssueRequestRepository.findById(payload.requestId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다. id=" + payload.requestId()));

        if (request.getStatus() != CouponIssueRequestStatus.PENDING) {
            log.info("이미 처리된 발급 요청 — requestId={}, status={}", request.getId(), request.getStatus());
            return;
        }

        try {
            UserCouponInfo issued = userCouponService.issue(payload.userId(), payload.couponId());
            request.markIssued(issued.id());
        } catch (CoreException e) {
            request.markFailed(e.getMessage());
        }
    }

    private CouponIssuePayload parse(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, CouponIssuePayload.class);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 발급 요청 페이로드 파싱에 실패했습니다: " + rawPayload);
        }
    }
}
