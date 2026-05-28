package com.loopers.application.order;

import com.loopers.domain.inventory.InventoryEntity;
import com.loopers.domain.inventory.InventoryService;
import com.loopers.domain.order.OrderEntity;
import com.loopers.domain.order.OrderItemEntity;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductEntity;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final InventoryService inventoryService;

    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> commands) {
        List<OrderItemEntity> items = commands.stream().map(command -> {
            ProductEntity product = productService.getProduct(command.productId());
            InventoryEntity inventory = inventoryService.getByProductId(command.productId());
            if (inventory.getQuantity() < command.quantity()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
            }
            return new OrderItemEntity(product.getId(), product.getName(), product.getPrice(), command.quantity());
        }).toList();

        OrderEntity order = orderService.createOrder(userId, items);

        Map<Long, Integer> productQuantities = items.stream()
                .collect(Collectors.toMap(OrderItemEntity::getProductId, OrderItemEntity::getQuantity));
        inventoryService.deductAll(productQuantities);

        return OrderInfo.from(order);
    }

    public OrderInfo getOrder(Long authUserId, Long orderId) {
        OrderEntity order = orderService.getOrder(orderId);
        if (!order.isOwnedBy(authUserId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        return OrderInfo.from(order);
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
