package com.loopers.application.order;

import java.util.List;

/**
 * 주문 생성 커맨드. couponId 는 UserCouponModel.id (발급된 쿠폰 ID), 미적용 시 null.
 */
public record OrderCreateCommand(Long userId, List<OrderItemCommand> items, Long couponId) {}
