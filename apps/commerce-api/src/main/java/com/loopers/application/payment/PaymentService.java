package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentModel create(Long orderId, CardType cardType, String cardNo, Long amount) {
        return paymentRepository.save(new PaymentModel(orderId, cardType, cardNo, amount));
    }

    @Transactional
    public void assignTransactionKey(Long orderId, String transactionKey) {
        PaymentModel payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제를 찾을 수 없습니다."));
        payment.assignTransactionKey(transactionKey);
    }

    @Transactional
    public PaymentModel success(String transactionKey) {
        PaymentModel payment = paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[transactionKey = " + transactionKey + "] 결제를 찾을 수 없습니다."));
        payment.success(transactionKey);
        return payment;
    }

    @Transactional
    public PaymentModel failByTransactionKey(String transactionKey, String reason) {
        PaymentModel payment = paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[transactionKey = " + transactionKey + "] 결제를 찾을 수 없습니다."));
        payment.fail(transactionKey, reason);
        return payment;
    }

    @Transactional
    public void failByOrderId(Long orderId, String reason) {
        PaymentModel payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제를 찾을 수 없습니다."));
        payment.fail(payment.getTransactionKey(), reason);
    }

    @Transactional(readOnly = true)
    public PaymentModel getById(Long id) {
        return paymentRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 결제를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<PaymentModel> findPendingBefore(ZonedDateTime threshold) {
        return paymentRepository.findPendingBefore(threshold);
    }

    @Transactional
    public void markConflictByOrderId(Long orderId) {
        PaymentModel payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제를 찾을 수 없습니다."));
        payment.markAsConflict();
    }

    @Transactional
    public void successByOrderId(Long orderId, String transactionKey) {
        PaymentModel payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제를 찾을 수 없습니다."));
        payment.success(transactionKey);
    }

    @Transactional(readOnly = true)
    public PaymentModel getByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[transactionKey = " + transactionKey + "] 결제를 찾을 수 없습니다."));
    }
}
