package com.loopers.order.application;

import com.loopers.coupon.domain.CouponService;
import com.loopers.coupon.domain.CouponUse;
import com.loopers.coupon.domain.vo.CouponDiscount;
import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItems;
import com.loopers.order.domain.OrderSearchPeriod;
import com.loopers.order.domain.OrderService;
import com.loopers.order.domain.vo.OrderAmountSnapshot;
import com.loopers.stock.domain.ProductStockService;
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
        OrderItems orderItems = orderItemFactory.create(command.items());
        OrderAmountSnapshot amountSnapshot = applyCoupon(command, orderItems);
        productStockService.deduct(orderItems.quantitiesByProductId());
        Order order = Order.create(command.userId(), orderItems, command.userCouponId(), amountSnapshot);
        return OrderInfo.from(orderService.saveOrder(order));
    }

    private OrderAmountSnapshot applyCoupon(CreateOrderCommand command, OrderItems orderItems) {
        long total = orderItems.calculateTotalPrice();
        CouponUse couponUse = CouponUse.create(command.userId(), command.userCouponId(), total, ZonedDateTime.now());
        CouponDiscount discount = couponService.use(couponUse);
        return OrderAmountSnapshot.withDiscount(total, discount.value());
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
