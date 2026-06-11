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

    @Transactional
    public void approvePayment(Long orderId, com.loopers.domain.payment.PaymentMethod method, String transactionId, java.time.LocalDateTime approvedAt) {
        OrderModel order = orderService.getOrder(orderId);
        
        // 1. 결제 내역 저장
        paymentService.savePayment(orderId, method, order.getTotalPaymentAmount(), transactionId, approvedAt);

        // 2. 주문 완료 처리
        orderService.completeOrder(orderId);

        // 3. 쿠폰 사용 완료 처리
        if (order.getCouponIssueId() != null) {
            couponService.completeCouponUse(order.getCouponIssueId(), order.getTotalOriginalAmount());
        }
    }

    public Long checkout(Long userId, OrderCheckoutRequest request) {
        Long orderId = null;
        try {
            // [트랜잭션 1] 주문 생성 및 재고 가선점
            OrderCreateRequest createRequest = new OrderCreateRequest(
                    request.items().stream()
                            .map(item -> new OrderCreateRequest.Item(item.productId(), item.quantity()))
                            .toList()
            );
            orderId = createOrderAndPreoccupyStock(userId, createRequest, request.couponIssueId());
        } catch (Exception e) {
            throw e;
        }

        // [트랜잭션 외부] PG사 결제 승인 요청
        OrderModel order = orderService.getOrder(orderId);
        com.loopers.domain.payment.PaymentGateway.PaymentGatewayResult pgResult = null;
        try {
            pgResult = paymentGateway.requestPayment(orderId, order.getTotalPaymentAmount(), request.paymentMethod());
        } catch (Exception e) {
            // 결제 요청 실패 시 보상 트랜잭션 (주문 취소 및 재고 원복)
            cancelOrderAndRestoreStock(orderId);
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 승인 요청 중 오류가 발생했습니다: " + e.getMessage());
        }

        try {
            // [트랜잭션 2] 결제 완료 처리 및 상태 업데이트
            approvePayment(orderId, request.paymentMethod(), pgResult.transactionId(), pgResult.approvedAt());
        } catch (Exception e) {
            // 승인 완료 후처리 실패 시 보상 트랜잭션 (주문 취소, 재고 원복 및 외부 PG 결제 승인 취소)
            cancelOrderAndRestoreStock(orderId);
            try {
                paymentGateway.cancelPayment(pgResult.transactionId(), order.getTotalPaymentAmount());
            } catch (Exception cancelEx) {
                log.error("PG 결제 취소 중 오류 발생. transactionId: {}, orderId: {}", pgResult.transactionId(), orderId, cancelEx);
            }
            throw e;
        }

        return orderId;
    }

    @Transactional
    public void cancelOrderAndRestoreStock(Long orderId) {
        OrderModel order = orderService.getOrder(orderId);
        
        // 1. 주문 취소 상태 변경
        orderService.cancelOrder(orderId);

        // 2. 재고 원복
        List<StockService.StockRequest> stockRequests = order.getItems().stream()
                .map(item -> new StockService.StockRequest(item.getProductId(), item.getQuantity()))
                .toList();
        stockService.increaseStocks(stockRequests);
    }
}
