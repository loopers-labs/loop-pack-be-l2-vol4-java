package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderService {

    private static final ZoneId ORDER_ZONE = ZoneId.of("Asia/Seoul");
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public OrderResult create(Long userId, List<OrderLine> rawLines, long discountAmount, Long usedCouponId) {
        OrderLines lines = OrderLines.of(rawLines);
        OrderModel order = OrderModel.create(userId, lines, discountAmount, usedCouponId);
        OrderModel saved = orderRepository.save(order);
        List<OrderItem> items = lines.values().stream()
            .map(line -> OrderItem.of(
                saved.getId(),
                line.productId(),
                line.quantity(),
                line.productName(),
                line.productPrice(),
                line.brandName()
            ))
            .toList();
        List<OrderItem> savedItems = orderItemRepository.saveAll(items);
        return new OrderResult(saved, savedItems);
    }

    @Transactional
    public OrderModel startPayment(Long userId, Long orderId) {
        OrderModel order = orderRepository.findById(orderId)
            .filter(it -> it.getUserId().equals(userId))
            .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다."));
        order.startPayment();
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResult> findMine(Long userId, OrderPeriod period) {
        List<OrderModel> orders = orderRepository.findByUserIdAndCreatedAtBetween(
            userId, period.fromAtStartOfDay(ORDER_ZONE), period.toExclusive(ORDER_ZONE));
        return groupWithItems(orders);
    }

    @Transactional(readOnly = true)
    public OrderResult findOneOwnedBy(Long userId, Long orderId) {
        OrderModel order = orderRepository.findById(orderId)
            .filter(it -> it.getUserId().equals(userId))
            .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다."));
        return new OrderResult(order, orderItemRepository.findByOrderId(orderId));
    }

    @Transactional(readOnly = true)
    public Page<OrderResult> findAll(int page, int size) {
        Page<OrderModel> orders = orderRepository.findAll(PageRequest.of(page, size, LATEST_FIRST));
        List<OrderResult> results = groupWithItems(orders.getContent());
        return new PageImpl<>(results, orders.getPageable(), orders.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OrderResult findOne(Long orderId) {
        OrderModel order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다."));
        return new OrderResult(order, orderItemRepository.findByOrderId(orderId));
    }

    private List<OrderResult> groupWithItems(List<OrderModel> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(OrderModel::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrderId = orderItemRepository.findByOrderIdIn(orderIds).stream()
            .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return orders.stream()
            .map(order -> new OrderResult(order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
            .toList();
    }
}
