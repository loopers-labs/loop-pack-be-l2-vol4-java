package com.loopers.application.coupon;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.application.metrics.EventEnvelope;
import com.loopers.infrastructure.coupon.CouponIssueUpdater;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 선착순 쿠폰 발급 처리 (Slice 4, Step3). {@code coupon-issue-requests} 배치를 받아 요청별로 수량을 확보하고 결과를 확정한다.
 *
 * <p><b>멱등</b>: {@code event_handled(consumer_group, event_id)}로 이미 처리한 eventId(=outbox PK)를 건너뛴다
 * (metrics와 동일 저장소, 그룹만 {@link #CONSUMER_GROUP}). 발급 반영과 처리 마킹을 <b>한 트랜잭션</b>으로 묶어
 * 재전달 시 이중 발급을 막는다(컨슈머가 커밋 후 ack).
 *
 * <p><b>선착순</b>: {@link CouponIssueUpdater#reserve}의 조건부 원자 UPDATE(영향 행 1=확보 / 0=소진)로
 * {@code issued_count <= total_quantity}를 어떤 동시성에서도 위반하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CouponIssuer {

    public static final String CONSUMER_GROUP = "coupon-issuer";

    private static final Logger log = LoggerFactory.getLogger(CouponIssuer.class);

    private final EventHandledRepository eventHandledRepository;
    private final CouponIssueUpdater couponIssueUpdater;

    @Transactional
    public void apply(List<EventEnvelope> envelopes) {
        // 배치 내 중복 eventId 제거(먼저 온 것 유지) — 멱등 1차 방어
        Map<Long, EventEnvelope> byEventId = new LinkedHashMap<>();
        for (EventEnvelope e : envelopes) {
            if (e.eventId() != null) {
                byEventId.putIfAbsent(e.eventId(), e);
            }
        }
        if (byEventId.isEmpty()) {
            return;
        }

        // 이미 처리한 eventId 제거 — 멱등 2차 방어(재전달 대비)
        Set<Long> handled = eventHandledRepository.findHandled(CONSUMER_GROUP, byEventId.keySet());
        List<Long> freshIds = byEventId.keySet().stream()
                .filter(id -> !handled.contains(id))
                .toList();
        if (freshIds.isEmpty()) {
            return;
        }

        int issued = 0;
        int soldOut = 0;
        for (Long id : freshIds) {
            EventEnvelope e = byEventId.get(id);
            if (!"COUPON_ISSUE_REQUESTED".equals(e.eventType())) {
                log.warn("알 수 없는 eventType 스킵(handled 마킹): eventId={}, type={}", id, e.eventType());
                continue;
            }
            JsonNode payload = e.payload();
            long requestId = payload.get("requestId").asLong();
            long userId = payload.get("userId").asLong();
            long couponId = payload.get("couponId").asLong();

            if (couponIssueUpdater.reserve(couponId) == 1) {
                couponIssueUpdater.insertUserCoupon(userId, couponId);
                couponIssueUpdater.markRequest(requestId, "ISSUED");
                issued++;
            } else {
                couponIssueUpdater.markRequest(requestId, "SOLD_OUT");
                soldOut++;
            }
        }

        eventHandledRepository.markHandled(CONSUMER_GROUP, freshIds);
        log.debug("선착순 쿠폰 발급 처리: fresh={}, issued={}, soldOut={}", freshIds.size(), issued, soldOut);
    }
}
