package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주문 유스케이스 조립.
 * 상품 조회(존재 확인) → 도메인 서비스(재고 차감 + 주문 생성)에 위임 → 영속화.
 */
@RequiredArgsConstructor
@Component
public class OrderFacade {
    private final OrderService orderService;
    private final ProductService productService;
    private final OrderRepository orderRepository;

    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> items) {
        if (userId == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "주문하려면 로그인이 필요합니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        List<OrderLine> lines = items.stream()
            .map(item -> {
                Product product = productService.getProduct(item.productId()); // 없으면 NOT_FOUND
                return new OrderLine(product, item.quantity());
            })
            .toList();

        // 도메인 서비스가 재고를 차감하고 주문을 생성한다.
        // 조회된 상품은 같은 트랜잭션의 영속 상태이므로 재고 변경은 커밋 시 반영된다.
        Order order = orderService.createOrder(userId, lines);
        Order saved = orderRepository.save(order);
        return OrderInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long orderId) {
        Order order = orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(Long userId) {
        return orderRepository.findByUserId(userId).stream()
            .map(OrderInfo::from)
            .toList();
    }
}
