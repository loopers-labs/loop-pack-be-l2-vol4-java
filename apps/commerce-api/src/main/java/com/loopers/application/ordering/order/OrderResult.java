package com.loopers.application.ordering.order;

import com.loopers.domain.ordering.order.Order;
import com.loopers.domain.ordering.order.OrderLine;
import com.loopers.domain.ordering.order.OrderStatus;
import com.loopers.domain.payment.payment.Payment;
import com.loopers.domain.payment.payment.PaymentStatus;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderResult {
    public record Summary(
        Long orderId,
        String userId,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        Long originalAmount,
        Long discountAmount,
        Long finalAmount,
        Long couponId,
        ZonedDateTime createdAt
    ) {
        public static Summary from(Order order, Payment payment) {
            return new Summary(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                resolvePaymentStatus(order, payment),
                order.getOriginalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getCouponId(),
                order.getCreatedAt()
            );
        }
    }

    public record Detail(
        Long orderId,
        String userId,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        String failureReason,
        Long originalAmount,
        Long discountAmount,
        Long finalAmount,
        Long couponId,
        List<Item> items
    ) {
        public static Detail from(Order order, Payment payment) {
            return new Detail(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                resolvePaymentStatus(order, payment),
                payment == null ? null : payment.getFailureReason(),
                order.getOriginalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getCouponId(),
                order.getLines().stream()
                    .map(Item::from)
                    .toList()
            );
        }
    }

    private static PaymentStatus resolvePaymentStatus(Order order, Payment payment) {
        if (payment == null && !order.requiresPayment()) {
            return PaymentStatus.NOT_REQUIRED;
        }
        if (payment == null) {
            throw new IllegalStateException("[orderId = " + order.getId() + "] 결제 정보가 없습니다.");
        }
        return payment.getStatus();
    }

    public record Item(
        Long productId,
        String productName,
        Integer quantity,
        Long unitPrice,
        Long lineAmount
    ) {
        private static Item from(OrderLine line) {
            return new Item(
                line.getProductId(),
                line.getProductName(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getLineAmount()
            );
        }
    }
}
