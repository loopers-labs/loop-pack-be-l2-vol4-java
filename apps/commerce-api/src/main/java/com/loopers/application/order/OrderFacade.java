package com.loopers.application.order;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponUseResult;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderProductCommand;
import com.loopers.domain.order.OrderProductProcessService;
import com.loopers.domain.order.OrderResult;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {
    private final OrderService orderService;
    private final ProductService productService;
    private final OrderProductProcessService orderProductProcessService;
    private final CouponService couponService;

    @Transactional
    public OrderInfo createOrder(String userLoginId, List<OrderProductCommand> commands, Long couponId) {
        // Cross-domain writes acquire locks in Product -> IssuedCoupon order to reduce deadlock risk.
        List<Product> products = lockProductsForOrder(commands);
        OrderResult result = orderProductProcessService.createOrder(userLoginId, commands, products);
        applyCouponAfterProductLocks(userLoginId, couponId, result.order());
        productService.saveProducts(products);
        return OrderInfo.from(orderService.saveOrder(result));
    }

    @Transactional
    public OrderInfo createOrder(String userLoginId, List<OrderProductCommand> commands) {
        return createOrder(userLoginId, commands, null);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(String userLoginId, LocalDate startAt, LocalDate endAt, Integer page, Integer size) {
        return orderService.getOrders(userLoginId, startAt, endAt, page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(String userLoginId, Long orderId) {
        return OrderInfo.from(orderService.getOrder(userLoginId, orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getAllOrders(Integer page, Integer size) {
        return orderService.getAllOrders(page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long orderId) {
        return OrderInfo.from(orderService.getOrder(orderId));
    }

    private List<Product> lockProductsForOrder(List<OrderProductCommand> commands) {
        return productService.findProductsByIdsForUpdate(productIdsInLockOrder(commands));
    }

    private List<Long> productIdsInLockOrder(List<OrderProductCommand> commands) {
        return commands.stream()
            .map(OrderProductCommand::productId)
            .distinct()
            .sorted()
            .toList();
    }

    private void applyCouponAfterProductLocks(String userLoginId, Long couponId, Order order) {
        if (couponId == null) {
            return;
        }
        CouponUseResult couponUseResult = couponService.useCoupon(
            userLoginId,
            couponId,
            order.getOriginalAmount(),
            ZonedDateTime.now()
        );
        order.applyDiscount(couponUseResult.discountAmount());
    }
}
