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

    @Transactional
    public Order createOrder(Long userId, List<OrderItem> items) {
        return orderRepository.save(new Order(userId, items));
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Order getOrderForUser(Long orderId, Long userId) {
        Order order = getOrder(orderId);
        if (!order.belongsTo(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 주문만 조회할 수 있습니다.");
        }
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByPeriod(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderRepository.findByUserIdAndOrderedAtBetween(userId, startAt, endAt);
    }

    @Transactional
    public Order cancelOrder(Long orderId, Long userId) {
        Order order = getOrderForUser(orderId, userId);
        order.cancel();
        return orderRepository.save(order);
    }
}
