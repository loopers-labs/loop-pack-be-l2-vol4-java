package com.loopers.application.order;

import com.loopers.application.product.ProductCachePort;
import com.loopers.domain.order.OrderCreationService;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 주문 생성은 상품 재고를 차감하므로, 차감된 상품들의 상세 캐시는 stale 가능.
 * 주문 성공 직후 해당 상품들의 상세 캐시를 무효화한다.
 */
@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderCreationService orderCreationService;
    private final OrderService orderService;
    private final ProductCachePort productCache;

    public OrderInfo placeOrder(OrderCriteria.Create criteria) {
        OrderModel order = orderCreationService.create(
            criteria.userId(),
            criteria.lines(),
            criteria.userCouponId()
        );
        for (OrderItemModel item : order.getItems()) {
            productCache.evictDetail(item.getProductId());
        }
        return OrderInfo.from(order);
    }

    public OrderInfo getOrder(Long orderId) {
        return OrderInfo.from(orderService.getOrder(orderId));
    }

    public List<OrderInfo> getMyOrders(Long userId) {
        List<OrderModel> orders = orderService.getOrdersByUser(userId);
        return orders.stream().map(OrderInfo::from).toList();
    }
}
