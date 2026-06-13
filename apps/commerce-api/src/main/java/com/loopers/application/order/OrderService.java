package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItemRepository;
import com.loopers.domain.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public Order createOrder(Long userId, Long issuedCouponId, BigDecimal originalPrice, BigDecimal discountAmount,
                             List<OrderItem> items) {
        Order order = orderRepository.save(new Order(userId, issuedCouponId, originalPrice, discountAmount));

        List<OrderItem> itemsWithOrderId = items.stream()
            .map(item -> item.withOrderId(order.getId()))
            .toList();
        orderItemRepository.saveAll(itemsWithOrderId);

        return order;
    }

    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));
    }

    public List<Order> getOrders(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderRepository.findAllByUserIdAndPeriod(userId, startAt, endAt);
    }

    public List<OrderItem> getOrderItems(Long orderId) {
        return orderItemRepository.findAllByOrderId(orderId);
    }

    public Page<Order> getOrdersForAdmin(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }
}
