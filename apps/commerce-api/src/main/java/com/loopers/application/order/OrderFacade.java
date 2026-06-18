package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.ProductSnapshot;
import com.loopers.application.order.OrderRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductRepository;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductFacade.StockRequest;
import com.loopers.application.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.application.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductFacade productFacade;
    private final CouponRepository couponRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    @Transactional
    public Long createOrder(Long userId, OrderCreateRequest request) {
        List<Long> productIds = request.items().stream()
                .map(OrderCreateRequest.Item::productId)
                .toList();

        List<ProductModel> products = productRepository.findByIds(productIds);
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.PRODUCT_NOT_FOUND, "일부 상품을 찾을 수 없습니다.");
        }

        Map<Long, ProductModel> productMap = products.stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p));

        List<StockRequest> stockRequests = request.items().stream()
                .map(item -> new StockRequest(item.productId(), item.quantity()))
                .toList();
        productFacade.decreaseStocks(stockRequests);

        OrderModel order = new OrderModel(userId);
        for (OrderCreateRequest.Item item : request.items()) {
            ProductModel product = productMap.get(item.productId());
            ProductSnapshot snapshot = new ProductSnapshot(product.getName(), product.getPrice(), "Brand Placeholder");
            OrderItemModel orderItem = new OrderItemModel(order, product.getId(), snapshot, item.quantity());
            order.addItem(orderItem);
        }

        return orderRepository.save(order).getId();
    }

    @Transactional
    public Long checkout(Long userId, OrderCheckoutRequest request) {
        List<StockRequest> stockRequests = request.items().stream()
                .map(item -> new StockRequest(item.productId(), item.quantity()))
                .toList();
        productFacade.decreaseStocksWithLock(stockRequests);

        List<Long> productIds = request.items().stream()
                .map(OrderCheckoutRequest.Item::productId)
                .toList();

        List<ProductModel> products = productRepository.findByIds(productIds);
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
        CouponIssue couponIssue = null;
        if (request.couponIssueId() != null) {
            couponIssue = couponRepository.findIssueById(request.couponIssueId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "조회할 수 없는 쿠폰발급내역입니다."));
            discount = couponIssue.calculateDiscount(totalOriginalAmount, LocalDateTime.now());
        }

        java.math.BigDecimal totalPaymentAmount = totalOriginalAmount.subtract(discount);

        OrderModel order = new OrderModel(userId, request.couponIssueId(), totalOriginalAmount, discount, totalPaymentAmount);
        for (OrderCheckoutRequest.Item item : request.items()) {
            ProductModel product = productMap.get(item.productId());
            ProductSnapshot snapshot = new ProductSnapshot(product.getName(), product.getPrice(), "Brand Placeholder");
            OrderItemModel orderItem = new OrderItemModel(order, product.getId(), snapshot, item.quantity());
            order.addItem(orderItem);
        }
        Long orderId = orderRepository.save(order).getId();

        PaymentGatewayResult pgResult = null;
        try {
            pgResult = paymentGateway.requestPayment(orderId, totalPaymentAmount, request.paymentMethod());
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 승인 요청 중 오류가 발생했습니다: " + e.getMessage());
        }

        PaymentModel payment = new PaymentModel(orderId, request.paymentMethod(), totalPaymentAmount, pgResult.transactionId(), pgResult.approvedAt());
        paymentRepository.save(payment);
        
        order.complete();
        orderRepository.save(order);
        
        if (couponIssue != null) {
            couponIssue.use(totalOriginalAmount, LocalDateTime.now());
            couponRepository.saveIssue(couponIssue);
        }

        return orderId;
    }
}
