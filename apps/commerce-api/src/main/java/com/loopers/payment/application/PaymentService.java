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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentService {

    private final OrderReader orderReader;
    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResult.Pending createPending(Long userId, String orderNumber) {
        OrderInfo order = orderReader.findForPayment(orderNumber)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND));
        if (!order.payable()) {
            throw new CoreException(ErrorType.BAD_REQUEST, PaymentErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        }
        if (paymentRepository.findActiveByOrderNumber(orderNumber).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, PaymentErrorCode.PAYMENT_ALREADY_IN_PROGRESS);
        }

        Payment saved;
        try {
            // IDENTITY 키라 save 시점에 즉시 INSERT — 동시 더블서밋이 가드를 함께 통과해도 유니크 제약이 여기서 막는다.
            saved = paymentRepository.save(Payment.create(userId, orderNumber, Money.of(order.finalAmount())));
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, PaymentErrorCode.PAYMENT_ALREADY_IN_PROGRESS);
        }
        log.info("결제 PENDING 생성 orderNumber={} paymentId={} amount={}", orderNumber, saved.getId(), saved.getAmount().value());
        return new PaymentResult.Pending(saved.getId(), saved.getOrderNumber(), saved.getAmount().value());
    }

    @Transactional
    public void assignTransaction(Long paymentId, String transactionKey, PgProvider pgProvider) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "거래키를 확정할 결제를 찾을 수 없습니다."));
        payment.assignTransaction(transactionKey, pgProvider);
        log.info("결제 거래키 확정 orderNumber={} paymentId={} transactionKey={} provider={}",
                payment.getOrderNumber(), paymentId, transactionKey, pgProvider);
    }

    @Transactional
    public void abandon(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "포기할 결제를 찾을 수 없습니다."));
        payment.markAbandoned(reason);
        log.error("ABANDONED 전환 paymentId={} orderNumber={} reason={}",
                paymentId, payment.getOrderNumber(), reason);
    }
}
