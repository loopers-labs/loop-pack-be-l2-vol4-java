package com.loopers.application.order;

import com.loopers.domain.brand.BrandReader;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.ProductSnapshot;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductReader;
import com.loopers.domain.product.ProductStockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final ProductReader productReader;
    private final ProductStockService productStockService;
    private final CartService cartService;
    private final BrandReader brandReader;

    /**
     * 주문 생성 (단일 트랜잭션)
     * 1. 상품 존재 + 재고 확인
     * 2. 재고 차감
     * 3. 장바구니 항목 삭제
     * 4. 주문 생성 (스냅샷 포함)
     */
    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 비어있을 수 없습니다.");
        }
        List<OrderItem> items = requests.stream()
            .map(req -> buildOrderItem(req.productId(), req.quantity()))
            .toList();

        // 재고 차감
        requests.forEach(req ->
            productStockService.decreaseStock(req.productId(), req.quantity()));

        // 장바구니에서 해당 상품 삭제
        requests.forEach(req ->
            cartService.getCartItems(userId).stream()
                .filter(c -> c.getProductId().equals(req.productId()))
                .findFirst()
                .ifPresent(c -> cartService.removeItem(c.getId(), userId)));

        Order order = orderService.createOrder(userId, items);
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long orderId, Long userId) {
        Order order = orderService.getOrderForUser(orderId, userId);
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderService.getOrdersByPeriod(userId, startAt, endAt).stream()
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional
    public OrderInfo cancelOrder(Long orderId, Long userId) {
        Order order = orderService.cancelOrder(orderId, userId);
        return OrderInfo.from(order);
    }

    private OrderItem buildOrderItem(Long productId, int quantity) {
        ProductModel product = productReader.getProduct(productId);
        String brandName = brandReader.getBrand(product.getBrandId()).getName();
        ProductSnapshot snapshot = new ProductSnapshot(
            product.getName(),
            product.getPrice(),
            brandName
        );
        return new OrderItem(productId, quantity, snapshot);
    }

    public record OrderRequest(Long productId, int quantity) {}
}
