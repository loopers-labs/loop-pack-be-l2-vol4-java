package com.loopers.application.order;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
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
 *   1. 쿠폰 사전 조회 (존재·소유권 확인)
 *   2. 활성 상품 배치 조회 + 존재 확인
 *   3. 재고 원자적 차감 (UPDATE ... WHERE quantity >= qty, affected=0 → BAD_REQUEST)
 *   4. [Domain] 주문 엔티티 조립
 *   5. 쿠폰 적용 (@Version 낙관적 락)
 *   6. 영속화 (CascadeType.ALL로 주문항목 함께 저장)
 */
@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final OrderDomainService orderDomainService;
    private final UserCouponRepository userCouponRepository;

    /**
     * FR-O-01. 주문 생성
     */
    @Transactional
    public OrderInfo createOrder(OrderCreateCommand command) {
        List<OrderItemCommand> itemCommands = command.items();
        if (itemCommands == null || itemCommands.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        // 1. 쿠폰 사전 조회 — 존재·소유권 확인 (fail fast)
        UserCouponModel userCoupon = null;
        if (command.couponId() != null) {
            userCoupon = userCouponRepository.findByIdWithCoupon(command.couponId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
            if (!userCoupon.getUserId().equals(command.userId())) {
                throw new CoreException(ErrorType.BAD_REQUEST, "본인 소유의 쿠폰만 사용할 수 있습니다.");
            }
        }

        // 2. 활성 상품 배치 조회
        List<Long> reqProductIds = itemCommands.stream().map(OrderItemCommand::productId).toList();
        Map<Long, ProductModel> existProductMap = productRepository.findAllActiveByIds(reqProductIds)
            .stream()
            .collect(Collectors.toMap(ProductModel::getId, p -> p));

        for (Long pid : reqProductIds) {
            if (!existProductMap.containsKey(pid)) {
                throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다. id=" + pid);
            }
        }

        if (reqProductIds.stream().distinct().count() != reqProductIds.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "중복된 상품이 포함된 주문입니다.");
        }

        Map<Long, Integer> quantityMap = itemCommands.stream()
            .collect(Collectors.toMap(OrderItemCommand::productId, OrderItemCommand::quantity));

        // 3. 재고 원자적 차감 (UPDATE ... WHERE quantity >= qty)
        // affected = 0 → 재고 부족; 예외 발생 시 트랜잭션 전체 롤백
        for (OrderItemCommand cmd : itemCommands) {
            if (cmd.quantity() <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
            }
            int affected = stockRepository.decreaseStock(cmd.productId(), cmd.quantity());
            if (affected == 0) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                    "재고가 부족합니다. productId=" + cmd.productId());
            }
        }

        // 4. 주문 엔티티 조립 (쿠폰 미적용 상태로 먼저 금액 확정)
        List<ProductModel> products = List.copyOf(existProductMap.values());
        OrderModel order = orderDomainService.buildOrder(command.userId(), products, quantityMap);

        // 5. 쿠폰 적용 — 사용 처리(@Version 낙관적 락) + 할인 금액 반영
        if (userCoupon != null) {
            userCoupon.use();
            int discount = userCoupon.getCoupon().calculateDiscount(order.getOriginalAmount());
            order.applyPricing(order.getOriginalAmount(), discount);
        }

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
