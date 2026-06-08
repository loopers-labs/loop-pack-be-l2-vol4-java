package com.loopers.application.order;

import com.loopers.domain.order.OrderDomainService;
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
 * 주문 Facade — Product / Stock / Order 도메인을 조합하는 Application Layer.
 *
 * 도메인 로직(재고 검증, 주문 엔티티 조립)은 OrderDomainService에 위임하고,
 * 이 클래스는 Repository 조회·영속화와 흐름 제어(orchestration)만 담당한다.
 *
 * createOrder 흐름:
 *   1. 활성 상품 배치 조회 + 존재 확인
 *   2. 재고 배치 조회
 *   3. [Domain] 재고 사전 검증 (전체 실패 원칙)
 *   4. 재고 차감 (StockModel.decrease → dirty checking으로 자동 반영)
 *   5. [Domain] 주문 엔티티 조립
 *   6. 영속화 (CascadeType.ALL로 주문항목 함께 저장)
 */
@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final OrderDomainService orderDomainService;

    /**
     * FR-O-01. 주문 생성
     */
    @Transactional
    public OrderInfo createOrder(OrderCreateCommand command) {
        List<OrderItemCommand> itemCommands = command.items();
        if (itemCommands == null || itemCommands.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        // 1. 활성 상품 배치 조회
        List<Long> reqProductIds = itemCommands.stream().map(OrderItemCommand::productId).toList();
        Map<Long, ProductModel> existProductMap = productRepository.findAllActiveByIds(reqProductIds)
            .stream()
            .collect(Collectors.toMap(ProductModel::getId, p -> p));

        for (Long pid : reqProductIds) {
            if (!existProductMap.containsKey(pid)) {
                throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다. id=" + pid);
            }
        }

        // 2. 재고 배치 조회
        Map<Long, StockModel> stockMap = itemCommands.stream()
            .collect(Collectors.toMap(
                OrderItemCommand::productId,
                cmd -> stockRepository.findByProductId(cmd.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "재고 정보를 찾을 수 없습니다. productId=" + cmd.productId()))
            ));

        Map<Long, Integer> quantityMap = itemCommands.stream()
            .collect(Collectors.toMap(OrderItemCommand::productId, OrderItemCommand::quantity));

        // 3. 재고 사전 검증 (Domain Service 위임 — 한 건이라도 부족하면 전체 실패)
        orderDomainService.validateStocks(stockMap, quantityMap);

        // 4. 재고 차감 (dirty checking으로 트랜잭션 커밋 시 자동 반영)
        stockMap.forEach((productId, stock) -> stock.decrease(quantityMap.get(productId)));

        // 5. 주문 엔티티 조립 (Domain Service 위임 — 스냅샷 포함 OrderModel 반환)
        List<ProductModel> products = List.copyOf(existProductMap.values());
        OrderModel order = orderDomainService.buildOrder(command.userId(), products, quantityMap);

        // 6. 영속화 (CascadeType.ALL — 주문항목 한 번에 저장)
        return OrderInfo.from(orderRepository.save(order));
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
     * FR-OA-01. 주문 목록 조회 (어드민)
     */
    @Transactional(readOnly = true)
    public Page<OrderInfo> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(OrderInfo::from);
    }

    /**
     * FR-OA-02. 주문 상세 조회 (어드민)
     */
    @Transactional(readOnly = true)
    public OrderInfo getOrderByAdmin(Long orderId) {
        return OrderInfo.from(
            orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."))
        );
    }
}
