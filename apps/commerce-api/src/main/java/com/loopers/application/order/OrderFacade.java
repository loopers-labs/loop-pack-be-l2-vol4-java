package com.loopers.application.order;

import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.PaymentCommand;
import com.loopers.domain.order.PaymentGateway;
import com.loopers.domain.order.PaymentResult;
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
    private final PaymentGateway paymentGateway;

    public OrderInfo placeOrder(String loginId, OrderCommand command) {
        UserModel user = userService.getMyInfo(loginId);
        List<OrderLine> lines = normalize(command.items());

        OrderModel pendingOrderModel = orderService.createPendingOrder(user.getId(), lines);

        PaymentResult result = paymentGateway.requestPayment(
                PaymentCommand.of(pendingOrderModel.getId(), pendingOrderModel.getTotalAmount())
        );

        if (result.success()) {
            OrderModel confirmed = orderService.confirm(pendingOrderModel.getId());
            return OrderInfo.from(confirmed);
        }

        orderService.fail(pendingOrderModel.getId());
        throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 승인에 실패했습니다: " + result.message());
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
