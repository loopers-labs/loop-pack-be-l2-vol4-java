package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;

/**
 * 주문 항목 1건의 응용 계층 출력 DTO.
 * Money/Quantity VO 를 원시형(long/int)으로 풀어 외부 계층으로 전달한다.
 */
public record OrderItemInfo(
        Long productId,
        int quantity,
        long unitPrice,
        String productName,
        String brandName,
        String imageUrl,
        long subtotal
) {
    public static OrderItemInfo from(OrderItemModel item) {
        return new OrderItemInfo(
                item.getProductId(),
                item.getQuantity().value(),
                item.getUnitPrice().amount(),
                item.getProductName(),
                item.getBrandName(),
                item.getImageUrl(),
                item.subtotal().amount()
        );
    }
}
