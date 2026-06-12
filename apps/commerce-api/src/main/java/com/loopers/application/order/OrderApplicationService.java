package com.loopers.application.order;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.domain.inventory.InventoryEntity;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.order.OrderEntity;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderSnapshot;
import com.loopers.domain.order.OrderSnapshotItem;
import com.loopers.domain.product.ProductEntity;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CouponApplicationService couponApplicationService;

    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> commands, Long couponId) {
        List<OrderSnapshotItem> snapshotItems = commands.stream()
                .map(cmd -> {
                    ProductEntity product = productRepository.find(cmd.productId())
                            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + cmd.productId() + "] 상품을 찾을 수 없습니다."));
                    long subtotal = product.getPrice() * cmd.quantity();
                    return new OrderSnapshotItem(product.getId(), product.getName(), product.getPrice(), cmd.quantity(), subtotal);
                })
                .toList();

        long originalAmount = snapshotItems.stream().mapToLong(OrderSnapshotItem::subtotal).sum();
        long discountAmount = couponId != null
                ? couponApplicationService.useCoupon(couponId, userId, originalAmount)
                : 0L;

        Map<Long, Integer> productQuantities = commands.stream()
                .collect(Collectors.toMap(OrderItemCommand::productId, OrderItemCommand::quantity));

        List<Long> productIds = productQuantities.keySet().stream().sorted().toList();
        List<InventoryEntity> inventories = inventoryRepository.findAllByProductIdsWithLock(productIds);
        if (inventories.size() != productIds.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 재고가 포함되어 있습니다.");
        }
        inventories.forEach(inventory -> {
            inventory.deduct(productQuantities.get(inventory.getProductId()));
            inventoryRepository.save(inventory);
        });

        OrderSnapshot snapshot = new OrderSnapshot(snapshotItems, originalAmount, discountAmount,
                originalAmount - discountAmount, couponId);
        return OrderInfo.from(orderRepository.save(new OrderEntity(userId, snapshot)));
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long authUserId, Long orderId) {
        OrderEntity order = findOrderOrThrow(orderId);
        if (!order.isOwnedBy(authUserId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo> getOrders(Long userId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable) {
        return orderRepository.findAllByUserId(userId, startAt, endAt, pageable).map(OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo> getAdminOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getAdminOrder(Long orderId) {
        return OrderInfo.from(findOrderOrThrow(orderId));
    }

    private OrderEntity findOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }
}
