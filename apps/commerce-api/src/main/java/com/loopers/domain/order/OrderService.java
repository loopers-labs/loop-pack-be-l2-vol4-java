package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public OrderModel createOrder(Long memberId, List<OrderItemCommand> items) {
        long totalAmount = items.stream()
                .mapToLong(item -> item.price() * item.quantity())
                .sum();

        OrderModel order = orderRepository.save(new OrderModel(memberId, totalAmount));

        List<OrderItemModel> orderItems = items.stream()
                .map(item -> new OrderItemModel(
                        order.getId(), item.productId(), item.productName(), item.price(), item.quantity()
                ))
                .toList();
        orderItemRepository.saveAll(orderItems);

        return order;
    }

    @Transactional(readOnly = true)
    public OrderModel getOrder(Long orderId) {
        return orderRepository.find(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<OrderModel> getOrders(Long memberId, ZonedDateTime startAt, ZonedDateTime endAt) {
        if (startAt != null && endAt != null) {
            return orderRepository.findAllByMemberIdAndCreatedAtBetween(memberId, startAt, endAt);
        }
        return orderRepository.findAllByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public List<OrderItemModel> getOrderItems(Long orderId) {
        return orderItemRepository.findAllByOrderId(orderId);
    }

    @Transactional
    public void cancelOrder(Long orderId) {
        getOrder(orderId).cancel();
    }
}
