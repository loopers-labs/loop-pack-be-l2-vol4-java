package com.loopers.application.order;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.Quantity;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderFacade {

    private final ProductRepository productRepository;

    private final BrandRepository brandRepository;

    private final OrderRepository orderRepository;

    public OrderInfo createOrder(Long userId, List<OrderItemCommand> itemCommands) {
        validateNoDuplicateProduct(itemCommands);

        List<OrderItemCommand> sortedItemCommands = itemCommands.stream()
            .sorted(Comparator.comparing(OrderItemCommand::productId))
            .toList();

        List<OrderItemModel> orderItems = new ArrayList<>();

        for (OrderItemCommand itemCommand : sortedItemCommands) {
            Quantity quantity = Quantity.from(itemCommand.quantity());
            ProductModel product = productRepository.getActiveById(itemCommand.productId());
            BrandModel brand = brandRepository.getActiveById(product.getBrandId());

            int decreasedCount = productRepository.decreaseStock(product.getId(), quantity.value());

            if (decreasedCount == 0) {
                throw new CoreException(ErrorType.CONFLICT, "상품 재고가 부족합니다.");
            }

            OrderItemModel orderItem = OrderItemModel.builder()
                .productId(product.getId())
                .productName(product.getName().value())
                .productBrandName(brand.getName().value())
                .unitPrice(product.getPrice().value())
                .rawQuantity(quantity.value())
                .build();

            orderItems.add(orderItem);
        }

        int totalPrice = orderItems.stream()
            .mapToInt(OrderItemModel::totalPrice)
            .sum();

        OrderModel order = OrderModel.builder()
            .userId(userId)
            .orderedAt(ZonedDateTime.now())
            .totalPrice(totalPrice)
            .build();

        OrderModel savedOrder = orderRepository.save(order, orderItems);

        return OrderInfo.from(savedOrder, orderItems);
    }

    private void validateNoDuplicateProduct(List<OrderItemCommand> itemCommands) {
        long distinctProductCount = itemCommands.stream()
            .map(OrderItemCommand::productId)
            .distinct()
            .count();

        if (distinctProductCount < itemCommands.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "같은 상품은 한 번에 한 번만 주문할 수 있습니다.");
        }
    }

    @Transactional(readOnly = true)
    public OrderInfo readMyOrder(Long authUserId, Long orderId) {
        OrderModel order = orderRepository.getActiveByIdAndUserId(orderId, authUserId);
        List<OrderItemModel> orderItems = orderRepository.findActiveItemsByOrderId(order.getId());

        return OrderInfo.from(order, orderItems);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo> readMyOrders(Long userId, LocalDate startAt, LocalDate endAt, int page, int size) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = startAt != null ? startAt : today.minusMonths(1);
        LocalDate endDate = endAt != null ? endAt : today;

        if (startDate.isAfter(endDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다.");
        }

        if (startDate.isAfter(today)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "조회 시작일은 오늘 이후일 수 없습니다.");
        }

        ZonedDateTime startInclusive = startDate.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime endExclusive = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault());

        return orderRepository.findActiveByUserIdAndOrderedAtBetween(userId, startInclusive, endExclusive, page, size)
            .map(order -> OrderInfo.from(order, orderRepository.findActiveItemsByOrderId(order.getId())));
    }

    @Transactional(readOnly = true)
    public Page<OrderAdminSummaryInfo> readOrders(int page, int size) {
        return orderRepository.findActiveByPage(page, size)
            .map(OrderAdminSummaryInfo::from);
    }

    @Transactional(readOnly = true)
    public OrderAdminInfo readOrder(Long orderId) {
        OrderModel order = orderRepository.getActiveById(orderId);
        List<OrderItemModel> orderItems = orderRepository.findActiveItemsByOrderId(order.getId());

        return OrderAdminInfo.from(order, orderItems);
    }
}
