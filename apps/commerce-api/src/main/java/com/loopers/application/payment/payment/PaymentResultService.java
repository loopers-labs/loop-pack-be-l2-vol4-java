package com.loopers.application.payment.payment;

import com.loopers.application.event.order.OrderEventPublisher;
import com.loopers.application.ordering.order.OrderCommandService;
import com.loopers.domain.ordering.order.Order;
import com.loopers.domain.payment.payment.Payment;
import com.loopers.domain.payment.payment.PaymentRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PaymentResultService {

    private static final String TIMEOUT_REASON = "TIMEOUT";

    private final PaymentRepository paymentRepository;
    private final OrderCommandService orderCommandService;
    private final OrderEventPublisher orderEventPublisher;

    @Transactional
    public PaymentProcessResult markSuccess(Long orderId, String transactionKey) {
        Payment payment = getPayment(orderId);
        if (!payment.isRequested()) {
            return PaymentProcessResult.noop(orderId);
        }

        Order order = orderCommandService.getOrderForPayment(orderId);
        if (!order.isPaymentPending()) {
            return PaymentProcessResult.noop(orderId);
        }

        payment.markSuccess(transactionKey);
        paymentRepository.save(payment);
        Order paidOrder = orderCommandService.markPaid(orderId);
        orderEventPublisher.publishOrderPaid(paidOrder);
        return PaymentProcessResult.success(orderId);
    }

    @Transactional
    public PaymentProcessResult expirePayment(Long orderId) {
        Payment payment = getPayment(orderId);
        if (!payment.isRequested()) {
            return PaymentProcessResult.noop(orderId);
        }

        return expireRequestedPayment(payment, orderId);
    }

    @Transactional
    public PaymentProcessResult failPaymentAndRestoreStock(Long orderId, String reason) {
        Payment payment = getPayment(orderId);
        if (!payment.isRequested()) {
            return PaymentProcessResult.noop(orderId);
        }

        payment.markFailed(reason);
        paymentRepository.save(payment);
        orderCommandService.markPaymentFailedAndRestoreStock(orderId);
        return PaymentProcessResult.failed(orderId);
    }

    @Transactional
    public PaymentProcessResult cancelPaymentAndRestoreStock(Long orderId, String reason) {
        Payment payment = getPayment(orderId);
        if (!payment.isRequested()) {
            return PaymentProcessResult.noop(orderId);
        }

        payment.markCanceled(reason);
        paymentRepository.save(payment);
        orderCommandService.markCanceledAndRestoreStock(orderId);
        return PaymentProcessResult.canceled(orderId);
    }

    private PaymentProcessResult expireRequestedPayment(Payment payment, Long orderId) {
        payment.markFailed(TIMEOUT_REASON);
        paymentRepository.save(payment);
        orderCommandService.markPaymentFailedAndRestoreStock(orderId);
        return PaymentProcessResult.expired(orderId);
    }

    private Payment getPayment(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }

        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제 정보를 찾을 수 없습니다."));
    }
}
