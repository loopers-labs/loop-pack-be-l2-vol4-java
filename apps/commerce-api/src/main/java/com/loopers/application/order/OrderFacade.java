package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.quantity.Quantity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {
    private final OrderService orderService;

    @Transactional
    public OrderInfo place(Long userId, List<OrderLineCommand> commands) {
        List<OrderLine> lines = commands.stream()
            .map(command -> new OrderLine(command.productId(), new Quantity(command.quantity())))
            .toList();
        Order order = orderService.place(userId, lines);
        return OrderInfo.from(order);
    }
}
