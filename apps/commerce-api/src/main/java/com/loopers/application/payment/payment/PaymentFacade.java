package com.loopers.application.payment.payment;

import com.loopers.domain.ordering.order.Order;
import com.loopers.domain.ordering.order.OrderRepository;
import com.loopers.domain.payment.gateway.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentGatewayCommand;
import com.loopers.domain.payment.gateway.PaymentGatewayResult;
import com.loopers.domain.payment.gateway.PaymentGatewayTransactionStatus;
import com.loopers.domain.payment.payment.Payment;
import com.loopers.domain.payment.payment.PaymentRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class PaymentFacade {

    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback";

    private final PaymentCommandService paymentCommandService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentResultService paymentResultService;

    public PaymentResult.Request requestPayment(PaymentCommand.Request command) {
        Order order = getOrder(command);
        Payment payment = getPayment(order.getId());
        validateRequestable(order, payment);

        PaymentGatewayResult gatewayResult = paymentGateway.requestPayment(new PaymentGatewayCommand.Request(
            command.userId(),
            toPgOrderId(order.getId()),
            command.cardType().name(),
            command.cardNo(),
            payment.getAmount(),
            CALLBACK_URL
        ));

        Payment processingPayment = paymentCommandService.markProcessing(order.getId(), gatewayResult.transactionKey());
        return PaymentResult.Request.from(processingPayment, "결제 요청이 접수되었습니다.");
    }

    public PaymentResult.Request handleCallback(PaymentCommand.Callback command) {
        Long orderId = fromPgOrderId(command.orderId());
        return applyGatewayResult(
            orderId,
            new PaymentGatewayResult(
                command.status() == PaymentCommand.TransactionStatus.SUCCESS,
                false,
                command.transactionKey(),
                command.reason(),
                command.status().toGatewayStatus(),
                command.orderId()
            )
        );
    }

    public PaymentResult.Request syncPayment(String userId, Long orderId) {
        Order order = getOrder(userId, orderId);
        Payment payment = getPayment(order.getId());
        Optional<PaymentGatewayResult> gatewayResult = payment.getTransactionKey() == null
            ? getLatestGatewayPayment(userId, order.getId())
            : paymentGateway.getPayment(userId, payment.getTransactionKey());

        return gatewayResult
            .map(result -> applyGatewayResult(order.getId(), result))
            .orElseGet(() -> PaymentResult.Request.from(payment, "확인 가능한 PG 결제 정보가 없습니다."));
    }

    private Order getOrder(PaymentCommand.Request command) {
        return getOrder(command.userId(), command.orderId());
    }

    private Order getOrder(String userId, Long orderId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    private Payment getPayment(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제 정보를 찾을 수 없습니다."));
    }

    private void validateRequestable(Order order, Payment payment) {
        if (!order.isPaymentPending()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 대기 주문만 결제를 요청할 수 있습니다.");
        }
        if (!payment.isRequested()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "요청 대기 결제만 결제를 요청할 수 있습니다.");
        }
    }

    private Optional<PaymentGatewayResult> getLatestGatewayPayment(String userId, Long orderId) {
        List<PaymentGatewayResult> results = paymentGateway.getPaymentsByOrderId(userId, toPgOrderId(orderId));
        return results.stream()
            .max(Comparator.comparing(PaymentGatewayResult::transactionKey));
    }

    private PaymentResult.Request applyGatewayResult(Long orderId, PaymentGatewayResult gatewayResult) {
        validateTransactionKey(orderId, gatewayResult);
        if (gatewayResult.transactionStatus() == PaymentGatewayTransactionStatus.PENDING) {
            Payment payment = paymentCommandService.markProcessing(orderId, gatewayResult.transactionKey());
            return PaymentResult.Request.from(payment, "결제 처리 중입니다.");
        }
        if (gatewayResult.transactionStatus() == PaymentGatewayTransactionStatus.SUCCESS) {
            paymentResultService.markSuccess(orderId, gatewayResult.transactionKey());
            return PaymentResult.Request.from(getPayment(orderId), "결제가 완료되었습니다.");
        }

        paymentResultService.failPaymentAndRestoreStock(orderId, gatewayResult.message());
        return PaymentResult.Request.from(getPayment(orderId), gatewayResult.message());
    }

    private void validateTransactionKey(Long orderId, PaymentGatewayResult gatewayResult) {
        Payment payment = getPayment(orderId);
        if (payment.getTransactionKey() == null) {
            return;
        }
        if (!payment.getTransactionKey().equals(gatewayResult.transactionKey())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 거래 키가 주문 결제 정보와 일치하지 않습니다.");
        }
    }

    private String toPgOrderId(Long orderId) {
        return String.format("%06d", orderId);
    }

    private Long fromPgOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 주문 ID는 필수입니다.");
        }
        try {
            return Long.parseLong(orderId);
        } catch (NumberFormatException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 주문 ID 형식이 올바르지 않습니다.");
        }
    }
}
