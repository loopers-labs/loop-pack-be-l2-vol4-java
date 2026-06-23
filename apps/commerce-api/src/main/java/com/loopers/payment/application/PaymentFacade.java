package com.loopers.payment.application;

import com.loopers.order.domain.OrderModel;
import com.loopers.order.domain.OrderRepository;
import com.loopers.payment.domain.PaymentModel;
import com.loopers.payment.domain.PaymentRepository;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.infrastructure.pg.PgPaymentClient;
import com.loopers.payment.infrastructure.pg.PgPaymentClientDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PgPaymentClient pgPaymentClient;

    @Value("${pg.callback-url}")
    private String callbackUrl;

    @Transactional
    public PaymentInfo requestPayment(Long userId, String loginId, Long orderId, String cardType, String cardNo) {
        OrderModel order = orderRepository.findWithLock(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "접근 권한이 없습니다.");
        }

        order.startPayment();
        orderRepository.save(order);

        PgPaymentClientDto.TransactionResponse pgResponse = pgPaymentClient.requestPayment(
            loginId,
            new PgPaymentClientDto.PaymentRequest(
                String.valueOf(orderId),
                cardType,
                cardNo,
                order.getFinalAmount(),
                callbackUrl
            )
        );

        PaymentModel payment = new PaymentModel(orderId, pgResponse.transactionKey(), cardType, order.getFinalAmount(), loginId);
        return PaymentInfo.from(paymentRepository.save(payment));
    }

    @Transactional
    public void recoverPayment(Long orderId, Long userId, String loginId) {
        OrderModel order = orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "접근 권한이 없습니다.");
        }

        PaymentModel payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        PgPaymentClientDto.TransactionResponse pgResponse = pgPaymentClient.getTransaction(loginId, payment.getTransactionKey());
        applyPgResult(payment, order, pgResponse.status());
    }

    @Transactional
    public void handleCallback(String transactionKey) {
        PaymentModel payment = paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        OrderModel order = orderRepository.find(payment.getOrderId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        // [fix] 콜백 발신지를 검증할 방법이 없어, 바디의 status를 그대로 믿는 대신 PG에 재조회한 실제 상태를 사용
        PgPaymentClientDto.TransactionResponse pgResponse = pgPaymentClient.getTransaction(payment.getLoginId(), transactionKey);
        applyPgResult(payment, order, pgResponse.status());
    }

    private void applyPgResult(PaymentModel payment, OrderModel order, String pgStatus) {
        if ("PENDING".equals(pgStatus)) {
            return;
        }

        if ("SUCCESS".equals(pgStatus)) {
            payment.confirm();
            order.confirm();
        } else {
            payment.fail();
            order.failPayment();
        }

        paymentRepository.save(payment);
        orderRepository.save(order);
    }
}
