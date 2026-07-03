package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestService;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.event.CouponIssueRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * 선착순 쿠폰 발급 요청 접수 Facade.
 * <p>
 * 요청은 즉시 처리하지 않고 Kafka 로 위임한다:
 * <ol>
 *   <li>쿠폰 템플릿 존재/만료 사전 검증 (실패 시 즉시 응답 — 큐에 담지 않음)</li>
 *   <li>UUID requestId 발급</li>
 *   <li>PENDING 상태의 CouponIssueRequest 저장</li>
 *   <li>ApplicationEvent 발행 → OutboxEventListener 가 outbox 저장 → Relay 가 Kafka 발행</li>
 * </ol>
 * 3~4번은 <b>같은 트랜잭션</b>에서 처리되어 원자성이 보장된다.
 */
@RequiredArgsConstructor
@Component
public class CouponIssueRequestFacade {

    private final CouponTemplateService couponTemplateService;
    private final CouponIssueRequestService couponIssueRequestService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CouponIssueRequestInfo enqueue(CouponIssueRequestCriteria.Enqueue criteria) {
        couponTemplateService.get(criteria.couponTemplateId()); // 존재 검증

        String requestId = UUID.randomUUID().toString();
        CouponIssueRequest saved = couponIssueRequestService.create(
            requestId, criteria.userId(), criteria.couponTemplateId());

        eventPublisher.publishEvent(new CouponIssueRequestedEvent(
            requestId, criteria.userId(), criteria.couponTemplateId(), ZonedDateTime.now()
        ));

        return CouponIssueRequestInfo.from(saved);
    }

    public CouponIssueRequestInfo getStatus(String requestId) {
        return CouponIssueRequestInfo.from(couponIssueRequestService.getByRequestId(requestId));
    }
}
