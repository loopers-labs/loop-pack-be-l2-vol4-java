package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.stock.StockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@RequiredArgsConstructor
@Component
@Transactional
public class PaymentFacade {

    private final OrderService orderService;
    private final StockService stockService;
    private final PaymentService paymentService;

    /** 결제 확정 — 금액 검증 + 재고 확정 + 주문 확정 + 결제 저장 */
    public PaymentInfo confirm(UUID orderId, String pgTransactionId, Long amount) {
        OrderModel order = orderService.get(orderId);
        if (!order.isPending()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태 주문만 결제 확정할 수 있습니다.");
        }
        if (!amount.equals(order.getPgAmount())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액이 주문 금액과 일치하지 않습니다.");
        }
        order.getItems().forEach(item -> stockService.confirm(item.getProductId(), item.getQuantity()));
        orderService.confirm(order);
        PaymentModel payment = paymentService.save(new PaymentModel(orderId, pgTransactionId, PaymentStatus.SUCCESS, amount));
        return PaymentInfo.from(payment);
    }

    /** 결제 실패 — PENDING 상태일 때만 재고 해제 + 주문 실패 처리 (멱등) */
    public PaymentInfo fail(UUID orderId, String pgTransactionId, Long amount) {
        OrderModel order = orderService.get(orderId);
        if (order.isPending()) {
            order.getItems().forEach(item -> stockService.release(item.getProductId(), item.getQuantity()));
            orderService.fail(order);
        }
        PaymentModel payment = paymentService.save(new PaymentModel(orderId, pgTransactionId, PaymentStatus.FAILED, amount));
        return PaymentInfo.from(payment);
    }
}
