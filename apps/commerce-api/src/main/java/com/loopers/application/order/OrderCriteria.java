package com.loopers.application.order;

import com.loopers.domain.order.OrderLine;

import java.util.List;

public final class OrderCriteria {

    private OrderCriteria() {}

    /**
     * @param userCouponId nullable — null 이면 쿠폰 미적용
     */
    public record Create(Long userId, List<OrderLine> lines, Long userCouponId) {}
}
