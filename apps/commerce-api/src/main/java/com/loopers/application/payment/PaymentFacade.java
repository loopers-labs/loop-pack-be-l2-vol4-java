package com.loopers.application.payment;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentFacade {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public PaymentInfo createPayment(Long userId, Long orderId, CardType cardType, String cardNo, ZonedDateTime now) {
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
        PaymentModel savedPayment = paymentRepository.save(payment);

        String transactionKey = paymentGateway.requestPayment(savedPayment);
        savedPayment.recordTransactionKey(transactionKey);

        return PaymentInfo.from(savedPayment);
    }

    public void handleCallback(Long orderId, String transactionKey, PaymentStatus result, String reason) {
        PaymentModel payment = paymentRepository.getByOrderId(orderId);

        if (!payment.matchesTransactionKey(transactionKey)) {
            throw new CoreException(ErrorType.FORBIDDEN, "콜백의 거래 식별자가 결제와 일치하지 않습니다.");
        }

        if (payment.isTerminal()) {
            return;
        }

        payment.confirm(result, reason);

        if (payment.isSuccess()) {
            orderRepository.getActiveById(orderId).markPaid();
        } else if (payment.isFailed()) {
            orderRepository.getActiveById(orderId).markPaymentFailed();
        }
    }
}
