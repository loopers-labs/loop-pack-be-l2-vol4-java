package com.loopers.interfaces.consumer;

import java.time.ZonedDateTime;

/**
 * coupon-issue-requests 의 메시지 계약 — producer(commerce-api)의 동일 record 와 필드가 일치해야 한다(wire contract).
 * 도메인 로직이 아니라 전송 계약이라 앱 간 복제한다. 처리 대상의 진실은 이 payload 가 아니라 DB 요청 행이며,
 * 여기서 신뢰하는 건 "어느 요청을 처리하라"는 {@code requestId} 뿐이다.
 */
public record CouponIssueMessage(
        String requestId,
        Long userId,
        Long templateId,
        ZonedDateTime occurredAt
) {
}
