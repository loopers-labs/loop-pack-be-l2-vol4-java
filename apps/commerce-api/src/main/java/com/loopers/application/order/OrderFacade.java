package com.loopers.application.order;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.StockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final StockService stockService;
    private final com.loopers.domain.coupon.CouponService couponService;
    private final com.loopers.domain.payment.PaymentService paymentService;
    private final com.loopers.domain.payment.PaymentGateway paymentGateway;

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
    public Long checkout(Long userId, OrderCheckoutRequest request) {
        // 1. [단일 트랜잭션] 재고 차감 (비관적 락 사용) - 영속성 컨텍스트에 락과 함께 먼저 로드하기 위해 가장 먼저 실행
        List<StockService.StockRequest> stockRequests = request.items().stream()
                .map(item -> new StockService.StockRequest(item.productId(), item.quantity()))
                .toList();
        stockService.decreaseStocksWithLock(stockRequests);

        // 2. 상품 조회 및 계산
        List<Long> productIds = request.items().stream()
                .map(OrderCheckoutRequest.Item::productId)
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
        if (request.couponIssueId() != null) {
            discount = couponService.calculateDiscount(request.couponIssueId(), totalOriginalAmount);
        }

        java.math.BigDecimal totalPaymentAmount = totalOriginalAmount.subtract(discount);

        // 3. 주문 생성 (PENDING)
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

        Long orderId = orderService.createPendingOrder(userId, orderItemRequests, request.couponIssueId(), totalOriginalAmount, discount, totalPaymentAmount);

        // 4. PG사 결제 승인 요청 (트랜잭션 내부)
        com.loopers.domain.payment.PaymentGateway.PaymentGatewayResult pgResult = null;
        try {
            pgResult = paymentGateway.requestPayment(orderId, totalPaymentAmount, request.paymentMethod());
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 승인 요청 중 오류가 발생했습니다: " + e.getMessage());
        }

        // 5. 결제 및 쿠폰 사용 완료 처리
        paymentService.savePayment(orderId, request.paymentMethod(), totalPaymentAmount, pgResult.transactionId(), pgResult.approvedAt());
        orderService.completeOrder(orderId);
        
        if (request.couponIssueId() != null) {
            couponService.completeCouponUse(request.couponIssueId(), totalOriginalAmount);
        }

        return orderId;
    }
}
