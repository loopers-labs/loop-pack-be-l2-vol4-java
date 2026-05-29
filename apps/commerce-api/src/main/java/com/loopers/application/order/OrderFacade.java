package com.loopers.application.order;

import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final UserService userService;

    public OrderInfo createOrder(String loginId, List<OrderLine> lines) {
        UserModel user = userService.getUser(loginId);
        OrderModel order = orderService.createOrder(user.getId(), lines);
        return OrderInfo.from(order);
    }
}
