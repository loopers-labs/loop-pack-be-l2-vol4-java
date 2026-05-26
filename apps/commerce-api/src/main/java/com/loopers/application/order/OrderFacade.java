package com.loopers.application.order;

import com.loopers.domain.order.OrderProductCommand;
import com.loopers.domain.order.OrderResult;
import com.loopers.domain.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;

    public OrderInfo createOrder(String userLoginId, List<OrderProductCommand> commands) {
        OrderResult result = orderService.createOrder(userLoginId, commands);
        return OrderInfo.from(result);
    }
}
