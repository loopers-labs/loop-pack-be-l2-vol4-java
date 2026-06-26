package com.loopers.application.payment;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentRequestResult;
import com.loopers.domain.payment.PaymentTransactionStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentTransactionWriter paymentTransactionWriter;

    public PaymentInfo createPayment(Long userId, Long orderId, CardType cardType, String cardNo, ZonedDateTime now) {
        PaymentModel acceptedPayment = acceptPayment(userId, orderId, cardType, cardNo, now);

        PaymentRequestResult requestResult = paymentGateway.requestPayment(acceptedPayment);

        acceptedPayment.applyRequestResult(requestResult);
        PaymentModel confirmedPayment = paymentRepository.save(acceptedPayment);

        return PaymentInfo.from(confirmedPayment);
    }

    private PaymentModel acceptPayment(Long userId, Long orderId, CardType cardType, String cardNo, ZonedDateTime now) {
        OrderModel order = orderRepository.getActiveByIdAndUserId(orderId, userId);

        if (paymentRepository.existsByOrderId(orderId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 결제가 진행 중이거나 완료된 주문입니다.");
        }

        PaymentModel payment = PaymentModel.builder()
            .orderId(orderId)
            .userId(userId)
            .amount(order.getFinalAmount())
            .cardType(cardType)
            .rawCardNo(cardNo)
            .requestedAt(now)
            .build();

        return paymentRepository.save(payment);
    }

    public void handleCallback(Long orderId, String transactionKey) {
        PaymentModel payment = paymentRepository.getByOrderId(orderId);

        if (!payment.matchesTransactionKey(transactionKey)) {
            throw new CoreException(ErrorType.FORBIDDEN, "콜백의 거래 식별자가 결제와 일치하지 않습니다.");
        }

        if (payment.isTerminal()) {
            return;
        }

        PaymentTransactionStatus verified = paymentGateway.queryTransaction(payment);
        if (!verified.isFound() || verified.isStillProcessing()) {
            return;
        }

        paymentTransactionWriter.confirm(payment, verified);
    }
}
