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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final UserService userService;

    /**
     * 주문 생성. 재고·쿠폰 차감은 주문 생성 시점에 수행하고 PENDING 으로 둔다.
     * 결제는 POST /api/v1/payments 로 별도 접수하며, 결과는 콜백/폴링으로 주문에 반영된다.
     */
    public OrderInfo placeOrder(String loginId, OrderCommand command) {
        UserModel user = userService.getMyInfo(loginId);
        List<OrderLine> lines = normalize(command.items());

        OrderModel pendingOrderModel = orderService.createPendingOrder(user.getId(), lines, command.couponId());

        return OrderInfo.from(pendingOrderModel);
    }

    private List<OrderLine> normalize(List<OrderCommand.Item> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 최소 1개 이상이어야 합니다.");
        }
        Map<Long, Integer> aggregated = new LinkedHashMap<>();
        for (OrderCommand.Item item : items) {
            OrderLine validated = OrderLine.of(item.productId(), item.quantity());
            aggregated.merge(validated.productId(), validated.quantity(), Integer::sum);
        }
        return aggregated.entrySet().stream()
                .map(e -> OrderLine.of(e.getKey(), e.getValue()))
                .toList();
    }
}
