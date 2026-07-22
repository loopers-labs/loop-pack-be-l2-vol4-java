package com.loopers.ranking.interfaces;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * order-events 에서 랭킹이 소비하는 필드. 라인별 상품·수량과 결제시각(paidAt)만 쓴다.
 * paidAt 을 occurredAt 으로 삼아 날짜를 버킷팅한다.
 */
public record OrderPaidMessage(
        List<Line> items,
        ZonedDateTime paidAt
) {
    public record Line(Long productId, int quantity) {
    }
}
