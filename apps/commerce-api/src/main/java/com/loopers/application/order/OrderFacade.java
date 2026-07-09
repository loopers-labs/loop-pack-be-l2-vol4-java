package com.loopers.application.order;

import com.loopers.application.coupon.CouponTemplateService;
import com.loopers.application.coupon.UserCouponService;
import com.loopers.domain.coupon.CouponDomainService;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.interfaces.event.order.OrderCreatedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final UserCouponService userCouponService;
    private final CouponTemplateService couponTemplateService;
    private final CouponDomainService couponDomainService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderInfo createOrder(Long memberId, List<OrderItemCommand> items, Long couponId) {
        long originalAmount = orderService.calculateTotalPrice(items);

        CouponTemplateModel template = null;
        if (couponId != null) {
            UserCouponModel userCoupon = userCouponService.getById(couponId);
            if (!userCoupon.getMemberId().equals(memberId)) {
                throw new CoreException(ErrorType.FORBIDDEN, "본인의 쿠폰만 사용할 수 있습니다.");
            }
            template = couponTemplateService.getById(userCoupon.getTemplateId());
            couponDomainService.validateMinOrderAmount(template, originalAmount);
            if (userCoupon.getStatus(template.getExpiredAt(), template.isBlocked()) != CouponStatus.AVAILABLE) {
                throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
            }
            userCoupon.use();
        }

        long discountAmount = couponDomainService.calculateDiscount(template, originalAmount);
        var order = orderService.create(memberId, items, couponId, originalAmount, discountAmount);
        var orderItems = orderService.getItemsByOrderId(order.getId());
        eventPublisher.publishEvent(OrderCreatedEvent.from(order, orderItems));
        return OrderInfo.of(order, orderItems);
    }

    public void cancelOrder(Long orderId, Long memberId) {
        orderService.cancel(orderId, memberId);
    }


    public List<OrderInfo> getOrders(Long memberId, LocalDate startAt, LocalDate endAt) {
        List<OrderModel> orders = orderService.getOrders(memberId, startAt, endAt);
        if (orders.isEmpty()) return List.of();
        List<Long> orderIds = orders.stream().map(OrderModel::getId).toList();
        Map<Long, List<OrderItemModel>> itemsByOrderId = orderService.getItemsByOrderIds(orderIds).stream()
            .collect(Collectors.groupingBy(OrderItemModel::getOrderId));
        return orders.stream()
            .map(order -> OrderInfo.of(order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
            .toList();
    }

    public OrderInfo getOrderDetail(Long orderId, Long memberId) {
        var order = orderService.getById(orderId);
        if (!order.isOwnedBy(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 조회할 수 있습니다.");
        }
        var orderItems = orderService.getItemsByOrderId(orderId);
        return OrderInfo.of(order, orderItems);
    }

    public List<OrderInfo> getAllOrders(LocalDate startAt, LocalDate endAt) {
        List<OrderModel> orders = orderService.getAllOrders(startAt, endAt);
        if (orders.isEmpty()) return List.of();
        List<Long> orderIds = orders.stream().map(OrderModel::getId).toList();
        Map<Long, List<OrderItemModel>> itemsByOrderId = orderService.getItemsByOrderIds(orderIds).stream()
            .collect(Collectors.groupingBy(OrderItemModel::getOrderId));
        return orders.stream()
            .map(order -> OrderInfo.of(order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
            .toList();
    }

    public OrderInfo getOrderDetailForAdmin(Long orderId) {
        var order = orderService.getById(orderId);
        var orderItems = orderService.getItemsByOrderId(orderId);
        return OrderInfo.of(order, orderItems);
    }
}
