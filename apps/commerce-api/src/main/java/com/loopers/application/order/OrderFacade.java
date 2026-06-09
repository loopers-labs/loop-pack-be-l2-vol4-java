package com.loopers.application.order;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponUse;
import com.loopers.domain.coupon.vo.CouponDiscount;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItems;
import com.loopers.domain.order.OrderSearchPeriod;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.vo.OrderPayment;
import com.loopers.domain.stock.ProductStockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderItemFactory orderItemFactory;
    private final ProductStockService productStockService;
    private final CouponService couponService;
    private final OrderService orderService;

    @Transactional
    public OrderInfo createOrder(CreateOrderCommand command) {
        ZonedDateTime orderedAt = ZonedDateTime.now();

        OrderItems orderItems = orderItemFactory.create(command.items());
        long total = orderItems.calculateTotalPrice();
        CouponUse couponUse = CouponUse.create(command.userId(), command.userCouponId(), total, orderedAt);
        CouponDiscount discount = couponService.use(couponUse);
        OrderPayment payment = OrderPayment.withDiscount(total, discount.amount().value());

        productStockService.deduct(command.orderQuantities());

        Order order = Order.create(command.userId(), orderItems, command.userCouponId(), payment);
        return OrderInfo.from(orderService.saveOrder(order));
    }

    @Transactional(readOnly = true)
    public PageResult<OrderInfo> getMyOrders(GetMyOrdersCommand command) {
        return orderService.getOrders(
                command.userId(),
                new PageQuery(command.page(), command.size()),
                OrderSearchPeriod.of(command.startAt(), command.endAt())
            )
            .map(OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getMyOrderDetail(Long orderId, Long userId) {
        Order order = orderService.getOrder(orderId);
        if (!order.isOrderedBy(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 사용자의 주문은 조회할 수 없습니다.");
        }
        return OrderInfo.from(order);
    }
}
