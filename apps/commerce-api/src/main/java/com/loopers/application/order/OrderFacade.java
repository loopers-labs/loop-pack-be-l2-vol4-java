package com.loopers.application.order;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductRepository;
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
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final com.loopers.domain.coupon.CouponService couponService;
    private final com.loopers.domain.payment.PaymentService paymentService;
    private final com.loopers.domain.payment.PaymentGateway paymentGateway;

    @Transactional
    public Long createOrder(Long userId, OrderCreateRequest request) {
        List<Long> productIds = request.items().stream()
                .map(OrderCreateRequest.Item::productId)
                .toList();

        List<ProductModel> products = productRepository.findByIdIn(productIds);
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.PRODUCT_NOT_FOUND, "?쇰? ?곹뭹??李얠쓣 ???놁뒿?덈떎.");
        }

        Map<Long, ProductModel> productMap = products.stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p));

        // ?ш퀬 李④컧 ?붿껌 ?앹꽦
        List<StockService.StockRequest> stockRequests = request.items().stream()
                .map(item -> new StockService.StockRequest(item.productId(), item.quantity()))
                .toList();
        stockService.decreaseStocks(stockRequests);

        // 二쇰Ц ?앹꽦 ?붿껌 ?앹꽦 (?ㅻ깄???ы븿)
        List<OrderService.OrderItemRequest> orderItemRequests = request.items().stream()
                .map(item -> {
                    ProductModel product = productMap.get(item.productId());
                    return new OrderService.OrderItemRequest(
                            product.getId(),
                            product.getName(),
                            product.getPrice(),
                            "Brand Placeholder", // ?ㅼ젣濡쒕뒗 Brand 議고쉶媛 ?꾩슂?????덉쓬
                            item.quantity()
                    );
                }).toList();

        return orderService.createOrder(userId, orderItemRequests);
    }

    @Transactional
    public Long checkout(Long userId, OrderCheckoutRequest request) {
        // 1. [?⑥씪 ?몃옖??뀡] ?ш퀬 李④컧 (鍮꾧??????ъ슜) - ?곸냽??而⑦뀓?ㅽ듃???쎄낵 ?④퍡 癒쇱? 濡쒕뱶?섍린 ?꾪빐 媛??癒쇱? ?ㅽ뻾
        List<StockService.StockRequest> stockRequests = request.items().stream()
                .map(item -> new StockService.StockRequest(item.productId(), item.quantity()))
                .toList();
        stockService.decreaseStocksWithLock(stockRequests);

        // 2. ?곹뭹 議고쉶 諛?怨꾩궛
        List<Long> productIds = request.items().stream()
                .map(OrderCheckoutRequest.Item::productId)
                .toList();

        List<ProductModel> products = productRepository.findByIdIn(productIds);
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.PRODUCT_NOT_FOUND, "?쇰? ?곹뭹??李얠쓣 ???놁뒿?덈떎.");
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

        // 3. 二쇰Ц ?앹꽦 (PENDING)
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

        // 4. PG??寃곗젣 ?뱀씤 ?붿껌 (?몃옖??뀡 ?대?)
        com.loopers.domain.payment.PaymentGateway.PaymentGatewayResult pgResult = null;
        try {
            pgResult = paymentGateway.requestPayment(orderId, totalPaymentAmount, request.paymentMethod());
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "寃곗젣 ?뱀씤 ?붿껌 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: " + e.getMessage());
        }

        // 5. 寃곗젣 諛?荑좏룿 ?ъ슜 ?꾨즺 泥섎━
        paymentService.savePayment(orderId, request.paymentMethod(), totalPaymentAmount, pgResult.transactionId(), pgResult.approvedAt());
        orderService.completeOrder(orderId);
        
        if (request.couponIssueId() != null) {
            couponService.completeCouponUse(request.couponIssueId(), totalOriginalAmount);
        }

        return orderId;
    }
}
