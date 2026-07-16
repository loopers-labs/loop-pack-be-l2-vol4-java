package com.loopers.application.order;

import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.queue.QueueService;
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
    private final QueueService queueService;

    /**
     * 주문 생성. 대기열 입장 토큰을 먼저 검증(관문)한 뒤 재고·쿠폰 차감을 수행하고 PENDING 으로 둔다.
     * 주문 생성이 성공하면 토큰을 소비(삭제)한다. 실패하면 토큰은 남아 TTL 내 재시도할 수 있다.
     * 결제는 POST /api/v1/payments 로 별도 접수하며, 결과는 콜백/폴링으로 주문에 반영된다.
     */
    public OrderInfo placeOrder(String loginId, String entryToken, OrderCommand command) {
        queueService.validateToken(loginId, entryToken);

        UserModel user = userService.getMyInfo(loginId);
        List<OrderLine> lines = normalize(command.items());

        OrderModel pendingOrderModel = orderService.createPendingOrder(user.getId(), lines, command.couponId());

        queueService.consumeToken(loginId);

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
