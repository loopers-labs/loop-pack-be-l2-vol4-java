package com.loopers.application.coupon;

import java.time.ZonedDateTime;

/**
 * coupon-issue-requests 토픽으로 나가는 메시지 계약(JSON). 이 앱이 발행(접수)하고 이 앱이 소비(발급)한다
 * (consumer 가 도메인 불변식을 소유해야 하므로 commerce-api 호스팅).
 *
 * <p>Kafka 파티셔닝 키는 {@code templateId} 다 — 같은 템플릿의 요청은 항상 같은 파티션 → 단일 소비자 스레드가
 * 순차 처리 → 락 없이 {@code issuedCount < issueLimit} 강제(파티션 직렬화). {@code requestId} 는 소비자 멱등의
 * 기준이자 폴링 핸들이다.</p>
 *
 * @param requestId  발급 요청 전역 유일 키(멱등 기준 · 폴링 핸들)
 * @param userId     발급 대상 사용자
 * @param templateId 발급 대상 템플릿(= 파티셔닝 키)
 * @param occurredAt 접수 시각
 */
public record CouponIssueMessage(
        String requestId,
        Long userId,
        Long templateId,
        ZonedDateTime occurredAt
) {
}
