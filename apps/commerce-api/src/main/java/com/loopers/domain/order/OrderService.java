package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public OrderModel placeInitial(Long userId, List<OrderItem> items) {
        return orderRepository.save(new OrderModel(userId, items));
    }

    @Transactional
    public OrderModel markSucceeded(Long orderId) {
        OrderModel order = loadOrThrow(orderId);
        order.markSucceeded();
        order.getItems().size();   // 호출자가 Tx 밖에서 OrderInfo로 변환할 수 있도록 LAZY 컬렉션 초기화
        return order;
    }

    @Transactional
    public void markFailed(Long orderId, String reason) {
        loadOrThrow(orderId).markFailed(reason);
    }

    @Transactional(readOnly = true)
    public OrderModel getById(Long id) {
        return loadOrThrow(id);
    }

    private OrderModel loadOrThrow(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 주문을 찾을 수 없습니다."));
    }
}
