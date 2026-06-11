package com.loopers.application.order;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.domain.inventory.InventoryService;
import com.loopers.domain.order.OrderEntity;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderSnapshot;
import com.loopers.domain.order.OrderSnapshotItem;
import com.loopers.domain.product.ProductEntity;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class OrderApplicationService {

    private final OrderService orderService;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final CouponApplicationService couponApplicationService;

    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> commands, Long couponId) {
        List<OrderSnapshotItem> snapshotItems = commands.stream()
                .map(cmd -> {
                    ProductEntity product = productService.getProduct(cmd.productId());
                    long subtotal = product.getPrice() * cmd.quantity();
                    return new OrderSnapshotItem(product.getId(), product.getName(), product.getPrice(), cmd.quantity(), subtotal);
                })
                .toList();

        long originalAmount = snapshotItems.stream().mapToLong(OrderSnapshotItem::subtotal).sum();

        long discountAmount = 0L;
        if (couponId != null) {
            discountAmount = couponApplicationService.useCoupon(couponId, userId, originalAmount);
        }

        Map<Long, Integer> productQuantities = commands.stream()
                .collect(Collectors.toMap(OrderItemCommand::productId, OrderItemCommand::quantity));
        inventoryService.deductAll(productQuantities);

        long finalAmount = originalAmount - discountAmount;
        OrderSnapshot snapshot = new OrderSnapshot(snapshotItems, originalAmount, discountAmount, finalAmount, couponId);
        OrderEntity order = orderService.createOrder(userId, snapshot);

        return OrderInfo.from(order);
    }

    public OrderInfo getOrder(Long authUserId, Long orderId) {
        return OrderInfo.from(orderService.getOrder(authUserId, orderId));
    }

    public Page<OrderInfo> getOrders(Long userId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable) {
        return orderService.getOrders(userId, startAt, endAt, pageable).map(OrderInfo::from);
    }

    public Page<OrderInfo> getAdminOrders(Pageable pageable) {
        return orderService.getAllOrders(pageable).map(OrderInfo::from);
    }

    public OrderInfo getAdminOrder(Long orderId) {
        return OrderInfo.from(orderService.getOrder(orderId));
    }
}
