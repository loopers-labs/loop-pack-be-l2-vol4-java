package com.loopers.payment.application;

import com.loopers.common.domain.Money;
import com.loopers.order.application.OrderInfo;
import com.loopers.order.application.OrderReader;
import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentErrorCode;
import com.loopers.payment.domain.PaymentRepository;
import com.loopers.payment.domain.PgProvider;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentService {

    private final OrderReader orderReader;
    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment createPending(Long orderId) {
        OrderInfo order = orderReader.findForPayment(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND));
        if (!order.payable()) {
            throw new CoreException(ErrorType.BAD_REQUEST, PaymentErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        }
        if (paymentRepository.findActiveByOrderId(orderId).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, PaymentErrorCode.PAYMENT_ALREADY_IN_PROGRESS);
        }

        Payment saved = paymentRepository.save(Payment.create(orderId, Money.of(order.finalAmount())));
        log.info("결제 PENDING 생성 orderId={} paymentId={} amount={}", orderId, saved.getId(), saved.getAmount().value());
        return saved;
    }

    @Transactional
    public void assignTransaction(Long paymentId, String transactionKey, PgProvider pgProvider) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "거래키를 확정할 결제를 찾을 수 없습니다."));
        payment.assignTransaction(transactionKey, pgProvider);
        log.info("결제 거래키 확정 orderId={} paymentId={} transactionKey={} provider={}",
                payment.getOrderId(), paymentId, transactionKey, pgProvider);
    }
}
