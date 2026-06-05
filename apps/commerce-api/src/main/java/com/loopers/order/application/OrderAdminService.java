package com.loopers.order.application;

import com.loopers.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 전용 주문 조회. 특정 userId 로 범위를 제한하지 않고 전체 주문을 조회한다.
 * 사용자 주문 생성/본인 주문 조회(OrderService)와 액터·관심사가 달라 분리한다.
 */
@RequiredArgsConstructor
@Component
public class OrderAdminService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<OrderResult.Summary> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(OrderResult.Summary::from)
                .toList();
    }
}
