package com.loopers.application.order;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.money.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
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
    private final CouponService couponService;

    /**
     * 재고 차감(OrderService)과 쿠폰 사용(CouponService)이 서로 다른 서비스에 흩어져 있으므로,
     * 전부 호출하는 이 자리에 트랜잭션 경계를 긋는다. 어느 하나라도 실패하면 모두 롤백된다.
     */
    @Transactional
    public OrderInfo place(Long userId, List<OrderLineCommand> commands, Long couponId) {
        List<OrderLine> lines = commands.stream()
            .map(command -> new OrderLine(command.productId(), new Quantity(command.quantity())))
            .toList();
        List<OrderItem> items = orderService.prepareItems(lines);
        Money discountAmount = couponId == null
            ? Money.ZERO
            : couponService.use(userId, couponId, Order.totalOf(items));
        Order order = orderService.complete(userId, items, discountAmount);
        return OrderInfo.from(order);
    }
}
