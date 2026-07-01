package com.loopers.application.payment;

import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStockService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional
public class PaymentSyncComponent {

    private final OrderService orderService;
    private final OrderStockService orderStockService;
    private final PaymentService paymentService;
    private final UserCouponService userCouponService;

    public PaymentInfo confirm(UUID orderId, String pgTransactionId, Long amount) {
        OrderModel order = orderService.getForUpdate(orderId);
        orderStockService.confirmOrder(order, amount);
        PaymentModel payment = paymentService.saveIfAbsent(
            orderId,
            new PaymentModel(orderId, pgTransactionId, PaymentStatus.SUCCESS, amount)
        );
        return PaymentInfo.from(payment);
    }

    public PaymentInfo fail(UUID orderId, String pgTransactionId, Long amount) {
        OrderModel order = orderService.getForUpdate(orderId);
        boolean failed = orderStockService.failOrder(order);
        if (failed) {
            userCouponService.releaseByOrderId(orderId);
        }
        PaymentModel payment = paymentService.saveIfAbsent(
            orderId,
            new PaymentModel(orderId, pgTransactionId, PaymentStatus.FAILED, amount)
        );
        return PaymentInfo.from(payment);
    }
}
