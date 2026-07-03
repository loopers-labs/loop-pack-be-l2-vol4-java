package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

import java.util.List;

public record OrderInfo(
    Long id,
    Long userId,
    OrderStatus status,
    Long originalPrice,
    Long discountAmount,
    Long finalPrice,
    Long userCouponId,
    List<OrderItemInfo> items
) {
    public static OrderInfo from(OrderModel order) {
        List<OrderItemInfo> items = order.getItems().stream()
            .map(OrderItemInfo::from)
            .toList();
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getOriginalPrice(),
            order.getDiscountAmount(),
            order.getFinalPrice(),
            order.getUserCouponId(),
            items
        );
    }

    public record OrderItemInfo(
        Long id,
        Long productId,
        String productName,
        String brandName,
        Long price,
        Integer quantity,
        String imageUrl,
        Long subtotal
    ) {
        public static OrderItemInfo from(OrderItemModel item) {
            return new OrderItemInfo(
                item.getId(),
                item.getProductId(),
                item.getProductNameSnapshot(),
                item.getBrandNameSnapshot(),
                item.getPriceSnapshot(),
                item.getQuantity(),
                item.getImageUrlSnapshot(),
                item.subtotal()
            );
        }
    }
}
