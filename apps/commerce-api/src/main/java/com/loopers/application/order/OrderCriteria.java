package com.loopers.application.order;

import java.util.List;

/**
 * 주문 요청 입력 DTO (응용 계층).
 * API request DTO 와 분리한다 (CLAUDE.md §8).
 * couponId 는 nullable — 쿠폰 미적용 주문이면 null.
 */
public record OrderCriteria(Long userId, Long couponId, List<Line> lines) {

    /** 쿠폰 미적용 주문 편의 생성자 */
    public OrderCriteria(Long userId, List<Line> lines) {
        this(userId, null, lines);
    }

    public record Line(Long productId, int quantity) {}
}
