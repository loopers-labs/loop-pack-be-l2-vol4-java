package com.loopers.application.order;

import java.util.List;

/**
 * 주문 요청 입력 DTO (응용 계층).
 * API request DTO 와 분리한다 (CLAUDE.md §8).
 */
public record OrderCriteria(Long userId, List<Line> lines) {
    public record Line(Long productId, int quantity) {}
}
