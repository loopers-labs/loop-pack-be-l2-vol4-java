package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 주문 생성 Facade — Product / Stock / Order 도메인을 조합해 주문 흐름을 orchestration한다.
 *
 * 주문 생성 흐름:
 *   1. 전체 상품 조회 + 삭제/부재 확인
 *   2. 전체 재고 사전 확인 (한 건이라도 부족하면 전체 실패)
 *   3. 재고 일괄 차감
 *   4. 주문 + 주문 항목 스냅샷 저장
 */
@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;

    /**
     * FR-O-01. 주문 생성
     */
    @Transactional
    public OrderInfo createOrder(OrderCreateCommand command) {
        List<OrderItemCommand> itemCommands = command.items();
        if (itemCommands == null || itemCommands.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        // 1. 전체 상품 조회
        List<Long> productIds = itemCommands.stream().map(OrderItemCommand::productId).toList();
        Map<Long, ProductModel> productMap = productRepository.findAllActiveByIds(productIds)
            .stream()
            .collect(Collectors.toMap(ProductModel::getId, p -> p));

        // 존재하지 않는 상품 확인 (삭제된 상품 포함)
        for (Long pid : productIds) {
            if (!productMap.containsKey(pid)) {
                throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다. id=" + pid);
            }
        }

        // 2. 재고 조회 및 사전 확인 (한 건이라도 부족하면 전체 실패)
        Map<Long, StockModel> stockMap = itemCommands.stream()
            .collect(Collectors.toMap(
                OrderItemCommand::productId,
                cmd -> stockRepository.findByProductId(cmd.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "재고 정보를 찾을 수 없습니다. productId=" + cmd.productId()))
            ));

        for (OrderItemCommand cmd : itemCommands) {
            StockModel stock = stockMap.get(cmd.productId());
            if (stock.getQuantity() < cmd.quantity()) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                    "재고가 부족합니다. productId=" + cmd.productId()
                        + " (요청: " + cmd.quantity() + ", 재고: " + stock.getQuantity() + ")");
            }
        }

        // 3. 재고 일괄 차감
        for (OrderItemCommand cmd : itemCommands) {
            stockMap.get(cmd.productId()).decrease(cmd.quantity());
        }

        // 4. 주문 + 주문 항목 생성
        OrderModel order = new OrderModel(command.userId());
        orderRepository.save(order); // 먼저 저장해서 PK 확보 (OrderItem FK 필요)

        for (OrderItemCommand cmd : itemCommands) {
            ProductModel product = productMap.get(cmd.productId());
            OrderItemModel item = new OrderItemModel(
                order,
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getBrand().getName(),
                cmd.quantity()
            );
            order.addItem(item);
        }

        orderRepository.save(order); // 항목 포함 저장
        return OrderInfo.from(order);
    }

    /**
     * FR-O-02. 본인 주문 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<OrderInfo> getMyOrders(Long userId, Pageable pageable) {
        return orderRepository.findAllByUserId(userId, pageable).map(OrderInfo::from);
    }

    /**
     * FR-O-03. 주문 상세 조회 (본인 것만)
     */
    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long userId, Long orderId) {
        OrderModel order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        return OrderInfo.from(order);
    }

    /**
     * FR-OA-01. 주문 목록 조회 (어드민 — 전체)
     */
    @Transactional(readOnly = true)
    public Page<OrderInfo> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(OrderInfo::from);
    }

    /**
     * FR-OA-02. 주문 상세 조회 (어드민 — 제약 없음)
     */
    @Transactional(readOnly = true)
    public OrderInfo getOrderByAdmin(Long orderId) {
        return OrderInfo.from(
            orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."))
        );
    }
}
