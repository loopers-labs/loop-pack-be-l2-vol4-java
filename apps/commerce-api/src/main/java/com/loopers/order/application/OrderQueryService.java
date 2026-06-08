package com.loopers.order.application;

import com.loopers.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderQueryService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<OrderResult.Summary> getMyOrders(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(OrderResult.Summary::from)
                .toList();
    }
}
