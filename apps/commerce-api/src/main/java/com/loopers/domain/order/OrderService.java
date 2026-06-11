package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public Long createOrder(Long userId, List<OrderItemRequest> items) {
        OrderModel order = new OrderModel(userId);

        for (OrderItemRequest item : items) {
            ProductSnapshot snapshot = new ProductSnapshot(item.name(), item.price(), item.brandName());
            OrderItemModel orderItem = new OrderItemModel(order, item.productId(), snapshot, item.quantity());
            order.addItem(orderItem);
        }

        return orderRepository.save(order).getId();
    }

    @Transactional
    public Long createPendingOrder(Long userId, List<OrderItemRequest> items, Long couponIssueId, java.math.BigDecimal totalOriginalAmount, java.math.BigDecimal totalDiscountAmount, java.math.BigDecimal totalPaymentAmount) {
        OrderModel order = new OrderModel(userId, couponIssueId, totalOriginalAmount, totalDiscountAmount, totalPaymentAmount);

        for (OrderItemRequest item : items) {
            ProductSnapshot snapshot = new ProductSnapshot(item.name(), item.price(), item.brandName());
            OrderItemModel orderItem = new OrderItemModel(order, item.productId(), snapshot, item.quantity());
            order.addItem(orderItem);
        }

        return orderRepository.save(order).getId();
    }

    public void completeOrder(Long orderId) {
        OrderModel order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
        order.complete();
        orderRepository.save(order);
    }

    public void cancelOrder(Long orderId) {
        OrderModel order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
        order.cancel();
        orderRepository.save(order);
    }

    public List<OrderModel> getOrders(Long userId) {
        return orderRepository.findAllByUserId(userId);
    }

    public OrderModel getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
    }

    public record OrderItemRequest(
        Long productId,
        String name,
        java.math.BigDecimal price,
        String brandName,
        int quantity
    ) {}
}
