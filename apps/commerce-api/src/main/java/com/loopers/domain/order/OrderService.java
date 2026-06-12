package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Order 단일 도메인 서비스 — 조회/상태 전이.
 * 다중 도메인 협력(주문 생성 시 재고 차감)은 OrderCreationService 가 담당한다.
 */
@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public OrderModel getOrder(Long id) {
        return orderRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<OrderModel> getOrdersByUser(Long userId) {
        return orderRepository.findAllByUserId(userId);
    }

    @Transactional
    public OrderModel markPaid(Long id) {
        OrderModel order = getOrder(id);
        order.markPaid();
        return orderRepository.save(order);
    }

    @Transactional
    public OrderModel markFailed(Long id) {
        OrderModel order = getOrder(id);
        order.markFailed();
        return orderRepository.save(order);
    }

    @Transactional
    public OrderModel save(OrderModel order) {
        return orderRepository.save(order);
    }
}
