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

/**
 * 재고 차감·쿠폰 사용·주문(PENDING) 저장을 하나의 트랜잭션으로 묶는 원자 단위.
 * 외부 결제(PG) 호출은 이 트랜잭션 밖(OrderFacade)에서 한다 — 외부 지연이 DB 커넥션을 점유하지 않도록.
 * 트랜잭션 경계 = 클래스 경계로 두어 self-invocation 함정을 구조적으로 배제한다.
 */
@RequiredArgsConstructor
@Component
public class OrderRegistrationService {
    private final OrderService orderService;
    private final CouponService couponService;

    @Transactional
    public Order register(Long userId, List<OrderLineCommand> commands, Long couponId) {
        List<OrderLine> lines = commands.stream()
            .map(command -> new OrderLine(command.productId(), new Quantity(command.quantity())))
            .toList();
        List<OrderItem> items = orderService.prepareItems(lines);
        Money discountAmount = couponId == null
            ? Money.ZERO
            : couponService.use(userId, couponId, Order.totalOf(items));
        return orderService.complete(userId, items, discountAmount, couponId);
    }
}
