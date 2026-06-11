package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;

public record OrderItemInfo(
        Long productId,
        String productName,
        Long unitPrice, // 상품 1개 단가 (주문 시점 스냅샷)
        Integer quantity,  // 수량
        Long subtotal  // subtotal = unitPrice * quantity
) {
    public static OrderItemInfo from(OrderItemModel item) {
        return new OrderItemInfo(
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.subtotal()
        );
    }
}