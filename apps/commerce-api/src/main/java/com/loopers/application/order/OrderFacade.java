package com.loopers.application.order;

import com.loopers.domain.order.OrderCreationService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderCreationService orderCreationService;
    private final OrderRepository orderRepository;

    @Transactional
    public OrderInfo placeOrder(OrderCriteria criteria) {
        // ① 응용 입력 DTO → 도메인 서비스 입력으로 변환
        List<OrderCreationService.OrderLine> lines = criteria.lines().stream()
                .map(l -> new OrderCreationService.OrderLine(l.productId(), l.quantity()))
                .toList();

        // ② 재고 차감 + 스냅샷 + Aggregate 조립 (도메인 서비스에 위임)
        OrderModel order = orderCreationService.create(criteria.userId(), lines);

        // ③ 저장 (cascade=ALL 이라 OrderItem 도 함께 INSERT)
        OrderModel saved = orderRepository.save(order);

        // ④ 출력 DTO 로 변환해서 반환
        return OrderInfo.from(saved);
    }
}
