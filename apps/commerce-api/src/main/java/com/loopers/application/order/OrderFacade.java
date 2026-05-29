package com.loopers.application.order;

import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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

    public OrderInfo getOrder(String loginId, Long orderId) {
        UserModel user = userService.getUser(loginId);
        OrderModel order = orderService.getOrder(orderId);
        if (!order.isOwnedBy(user.getId())) {
            // 타 유저의 주문은 존재를 드러내지 않고 NOT_FOUND 로 응답
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        return OrderInfo.from(order);
    }
}
