package com.loopers.application.order;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.StockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final StockService stockService;
    private final com.loopers.domain.coupon.CouponService couponService;

    @Transactional
    public Long createOrder(Long userId, OrderCreateRequest request) {
        List<Long> productIds = request.items().stream()
                .map(OrderCreateRequest.Item::productId)
                .toList();

        List<ProductModel> products = productService.getProductsByIds(productIds);
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.PRODUCT_NOT_FOUND, "일부 상품을 찾을 수 없습니다.");
        }

        Map<Long, ProductModel> productMap = products.stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p));

        // 재고 차감 요청 생성
        List<StockService.StockRequest> stockRequests = request.items().stream()
                .map(item -> new StockService.StockRequest(item.productId(), item.quantity()))
                .toList();
        stockService.decreaseStocks(stockRequests);

        // 주문 생성 요청 생성 (스냅샷 포함)
        List<OrderService.OrderItemRequest> orderItemRequests = request.items().stream()
                .map(item -> {
                    ProductModel product = productMap.get(item.productId());
                    return new OrderService.OrderItemRequest(
                            product.getId(),
                            product.getName(),
                            product.getPrice(),
                            "Brand Placeholder", // 실제로는 Brand 조회가 필요할 수 있음
                            item.quantity()
                    );
                }).toList();

        return orderService.createOrder(userId, orderItemRequests);
    }

    @Transactional
    public Long createOrderAndPreoccupyStock(Long userId, OrderCreateRequest request, Long couponIssueId) {
        List<Long> productIds = request.items().stream()
                .map(OrderCreateRequest.Item::productId)
                .toList();

        List<ProductModel> products = productService.getProductsByIds(productIds);
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.PRODUCT_NOT_FOUND, "일부 상품을 찾을 수 없습니다.");
        }

        Map<Long, ProductModel> productMap = products.stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p));

        java.math.BigDecimal totalOriginalAmount = request.items().stream()
                .map(item -> {
                    ProductModel product = productMap.get(item.productId());
                    return product.getPrice().multiply(java.math.BigDecimal.valueOf(item.quantity()));
                })
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal discount = java.math.BigDecimal.ZERO;
        if (couponIssueId != null) {
            discount = couponService.calculateDiscount(couponIssueId, totalOriginalAmount);
        }

        java.math.BigDecimal totalPaymentAmount = totalOriginalAmount.subtract(discount);

        // 재고 가선점 (비관적 락 사용)
        List<StockService.StockRequest> stockRequests = request.items().stream()
                .map(item -> new StockService.StockRequest(item.productId(), item.quantity()))
                .toList();
        stockService.decreaseStocksWithLock(stockRequests);

        // 주문 생성 요청 생성
        List<OrderService.OrderItemRequest> orderItemRequests = request.items().stream()
                .map(item -> {
                    ProductModel product = productMap.get(item.productId());
                    return new OrderService.OrderItemRequest(
                            product.getId(),
                            product.getName(),
                            product.getPrice(),
                            "Brand Placeholder",
                            item.quantity()
                    );
                }).toList();

        return orderService.createPendingOrder(userId, orderItemRequests, couponIssueId, totalOriginalAmount, discount, totalPaymentAmount);
    }
}
