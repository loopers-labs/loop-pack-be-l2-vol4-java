package com.loopers.application.order;

import com.loopers.domain.coupon.CouponUseCommand;
import com.loopers.domain.stock.StockDeduction;

import java.time.ZonedDateTime;
import java.util.List;

public record CreateOrderCommand(
    Long userId,
    List<Item> items,
    Long userCouponId
) {

    public CreateOrderCommand {
        items = List.copyOf(items);
    }

    public CreateOrderCommand(Long userId, List<Item> items) {
        this(userId, items, null);
    }

    public CouponUseCommand couponUseCommand(long orderAmount, ZonedDateTime orderedAt) {
        return CouponUseCommand.forOrder(userId, userCouponId, orderAmount, orderedAt);
    }

    public List<Long> requestedProductIds() {
        return items.stream()
            .map(Item::productId)
            .distinct()
            .toList();
    }

    public List<StockDeduction> stockDeductions() {
        return items.stream()
            .map(item -> new StockDeduction(item.productId(), item.quantity()))
            .toList();
    }

    public record Item(
        Long productId,
        int quantity
    ) {
    }
}
