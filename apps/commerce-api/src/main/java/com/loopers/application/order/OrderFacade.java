package com.loopers.application.order;

import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.order.OrderItemInput;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.vo.Money;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OrderService orderService;
    private final ProductStockService productStockService;
    private final UserCouponService userCouponService;

    @Transactional
    public OrderInfo createOrder(String orderNumber, Long userId, List<OrderItemInput> items, Long userCouponId) {
        List<OrderLine> lines = new ArrayList<>();
        for (OrderItemInput input : OrderItemInput.merge(items)) {
            ProductStockModel stock = productStockService.decrease(input.stockId(), input.quantity());
            lines.add(new OrderLine(stock.getId(), stock.getProduct().getId(),
                    stock.getProduct().getName(), stock.getPrice(), input.quantity()));
        }
        long originalTotal = lines.stream().mapToLong(OrderLine::amount).sum();
        UserCouponService.UseResult amounts = userCouponService.use(userCouponId, userId, originalTotal);
        try {
            return OrderInfo.from(orderService.placeOrder(new OrderModel(orderNumber, userId, userCouponId), lines,
                    new Money(amounts.originalAmount()), new Money(amounts.discountAmount())));
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 처리된 주문입니다.");
        }
    }

    @Transactional
    public OrderInfo cancelOrder(Long orderId, Long userId) {
        OrderModel order = orderService.cancel(orderId, userId);
        order.getItems().forEach(item -> productStockService.increase(item.getStockId(), item.getQuantity().getValue()));
        userCouponService.revert(order.getUserCouponId());
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo> getAdminOrders(Pageable pageable) {
        return orderService.getAdminList(pageable).map(OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getAdminOrder(Long orderId) {
        return OrderInfo.from(orderService.get(orderId));
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long orderId, Long userId) {
        return OrderInfo.from(orderService.getByUser(orderId, userId));
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(Long userId, LocalDate startAt, LocalDate endAt) {
        ZonedDateTime start = startAt != null ? startAt.atStartOfDay(KST) : null;
        ZonedDateTime end = endAt != null ? endAt.atTime(LocalTime.MAX).atZone(KST) : null;
        return orderService.getList(userId, start, end).stream()
                .map(OrderInfo::from)
                .toList();
    }

}
