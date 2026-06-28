package com.loopers.order.application;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public Order save(Order order) {
        return orderRepository.save(order);
    }

    public Order get(Long orderId) {
        return orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    /** 본인 주문만 조회한다. 타인의 주문은 조회할 수 없다. */
    public Order getForMember(Long memberId, Long orderId) {
        Order order = get(orderId);
        if (!order.belongsTo(memberId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        return order;
    }

    public List<Order> getMyOrders(Long memberId) {
        return orderRepository.findByMemberId(memberId);
    }

    public List<Order> getAll() {
        return orderRepository.findAll();
    }
}
