package com.loopers.domain.event;

import java.time.ZonedDateTime;

/**
 * 선착순 쿠폰 발급 요청. requestId 는 사용자 개별 요청을 추적하는 멱등키.
 * Consumer 는 couponTemplateId 를 파티션 키로 받아 같은 쿠폰의 요청들을 단일 파티션에서 순차 처리한다.
 */
public record CouponIssueRequestedEvent(
    String requestId,
    Long userId,
    Long couponTemplateId,
    ZonedDateTime requestedAt
) {}
