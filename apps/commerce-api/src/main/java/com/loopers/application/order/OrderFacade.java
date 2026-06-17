package com.loopers.application.order;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.ProductModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final CouponService couponService;
    private final ProductDomainService productDomainService;

    @Transactional
    public OrderInfo placeOrder(Long memberId, List<OrderItemRequest> items, Long userCouponId) {
        // 재고 차감 (비관적 락) + OrderItemCommand 생성
        List<OrderItemCommand> orderItems = items.stream()
                .map(item -> {
                    ProductModel product = productDomainService.deductStock(item.productId(), item.quantity());
                    return new OrderItemCommand(product.getId(), product.getName(), product.getPrice(), item.quantity());
                })
                .toList();

        long originalAmount = orderItems.stream()
                .mapToLong(cmd -> cmd.price() * cmd.quantity())
                .sum();

        // 쿠폰 적용 (비관적 락으로 단 한 번만 사용 보장)
        long discountAmount = 0L;
        if (userCouponId != null) {
            discountAmount = couponService.validateAndUseCoupon(memberId, userCouponId, originalAmount);
        }

        OrderModel order = orderService.createOrder(memberId, orderItems, userCouponId, discountAmount);
        return OrderInfo.from(order);
    }
}
