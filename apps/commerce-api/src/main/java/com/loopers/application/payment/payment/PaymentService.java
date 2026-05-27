package com.loopers.application.payment.payment;

import com.loopers.domain.ordering.order.Order;
import com.loopers.domain.ordering.order.OrderRepository;
import com.loopers.domain.payment.gateway.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentGatewayResult;
import com.loopers.domain.payment.payment.Payment;
import com.loopers.domain.payment.payment.PaymentRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Service
public class PaymentService {

    private static final Duration PAYMENT_TIMEOUT = Duration.ofMinutes(1);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentResultService paymentResultService;

    public PaymentProcessResult processRequestedPayment(Long orderId) {
        Payment payment = getPayment(orderId);
        if (!payment.isRequested()) {
            return PaymentProcessResult.noop(orderId);
        }

        Order order = getOrder(orderId);
        if (!order.isPaymentPending()) {
            return PaymentProcessResult.noop(orderId);
        }
        if (isExpired(order)) {
            return paymentResultService.expirePayment(orderId);
        }

        PaymentGatewayResult authorizeResult = paymentGateway.authorize(orderId, payment.getAmount(), String.valueOf(orderId));
        if (authorizeResult.canceled()) {
            return paymentResultService.cancelPaymentAndRestoreStock(
                orderId,
                messageOrDefault(authorizeResult.message(), "결제가 취소되었습니다.")
            );
        }
        if (!authorizeResult.success()) {
            return paymentResultService.failPaymentAndRestoreStock(
                orderId,
                messageOrDefault(authorizeResult.message(), "결제 승인에 실패했습니다.")
            );
        }

        PaymentGatewayResult captureResult = paymentGateway.capture(authorizeResult.transactionKey());
        if (captureResult.success()) {
            return paymentResultService.markSuccess(orderId, authorizeResult.transactionKey());
        }

        paymentGateway.voidAuthorization(authorizeResult.transactionKey());
        if (captureResult.canceled()) {
            return paymentResultService.cancelPaymentAndRestoreStock(
                orderId,
                messageOrDefault(captureResult.message(), "결제가 취소되었습니다.")
            );
        }

        return paymentResultService.failPaymentAndRestoreStock(
            orderId,
            messageOrDefault(captureResult.message(), "결제 매입에 실패했습니다.")
        );
    }

    private Payment getPayment(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }

        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제 정보를 찾을 수 없습니다."));
    }

    private Order getOrder(Long orderId) {
        return orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    private boolean isExpired(Order order) {
        ZonedDateTime createdAt = order.getCreatedAt();
        return createdAt != null && createdAt.plus(PAYMENT_TIMEOUT).isBefore(ZonedDateTime.now());
    }

    private String messageOrDefault(String message, String defaultMessage) {
        if (message == null || message.isBlank()) {
            return defaultMessage;
        }

        return message;
    }
}
