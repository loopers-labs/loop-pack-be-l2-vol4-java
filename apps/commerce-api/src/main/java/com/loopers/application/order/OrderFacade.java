package com.loopers.application.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 주문 흐름을 조율하는 오케스트레이터. 트랜잭션을 직접 걸지 않는다.
 * 원자적 주문생성(재고+쿠폰+주문 저장)은 OrderRegistrationService 의 트랜잭션에 위임한다.
 * 결제는 주문 생성과 분리되어 별도 API(PaymentFacade.pay)로 트리거된다.
 */
@RequiredArgsConstructor
@Component
public class OrderFacade {
    private final OrderRegistrationService orderRegistrationService;

    public OrderInfo place(Long userId, List<OrderLineCommand> commands, Long couponId) {
        return OrderInfo.from(orderRegistrationService.register(userId, commands, couponId));
    }
}
