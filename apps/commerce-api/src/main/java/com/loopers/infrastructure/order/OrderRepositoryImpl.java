package com.loopers.infrastructure.order;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public OrderModel save(OrderModel order, List<OrderItemModel> orderItems) {
        OrderModel savedOrder = orderJpaRepository.save(order);
        orderItems.forEach(orderItem -> orderItem.assignOrder(savedOrder.getId()));
        orderItemJpaRepository.saveAll(orderItems);

        return savedOrder;
    }

    @Override
    public OrderModel getActiveById(Long orderId) {
        return orderJpaRepository.findByIdAndDeletedAtIsNull(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문이 존재하지 않습니다."));
    }

    @Override
    public OrderModel getActiveByIdAndUserId(Long orderId, Long userId) {
        return orderJpaRepository.findByIdAndUserIdAndDeletedAtIsNull(orderId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문이 존재하지 않습니다."));
    }

    @Override
    public Page<OrderModel> findActiveByUserIdAndOrderedAtBetween(Long userId, ZonedDateTime startInclusive, ZonedDateTime endExclusive, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderedAt"));

        return orderJpaRepository.findByUserIdAndDeletedAtIsNullAndOrderedAtGreaterThanEqualAndOrderedAtLessThan(
            userId, startInclusive, endExclusive, pageable);
    }

    @Override
    public Page<OrderModel> findActiveByPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderedAt"));

        return orderJpaRepository.findByDeletedAtIsNull(pageable);
    }

    @Override
    public List<OrderItemModel> findActiveItemsByOrderId(Long orderId) {
        return orderItemJpaRepository.findByOrderIdAndDeletedAtIsNull(orderId);
    }
}
