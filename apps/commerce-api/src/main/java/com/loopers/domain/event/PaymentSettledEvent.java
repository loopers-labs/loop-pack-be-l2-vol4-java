package com.loopers.domain.event;

import java.time.ZonedDateTime;

/**
 * 결제가 최종 확정(콜백/복구 폴링) 됐음.
 * outcome: SUCCEEDED / FAILED — 결제 결과의 최종 상태.
 */
public record PaymentSettledEvent(
    Long paymentId,
    Long orderId,
    Long userId,
    Outcome outcome,
    Long amount,
    ZonedDateTime occurredAt
) {
    public enum Outcome { SUCCEEDED, FAILED }
}
