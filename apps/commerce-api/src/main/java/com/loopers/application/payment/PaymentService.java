package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment createPayment(Long userId, Long orderId, CardType cardType, String cardNo, Long amount) {
        return paymentRepository.save(new Payment(userId, orderId, cardType, cardNo, amount));
    }

    @Transactional
    public Payment inProgress(Payment payment, String transactionKey) {
        payment.markInProgress(transactionKey);
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment complete(String transactionKey, PaymentStatus status, String reason) {
        Payment payment = paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다."));
        payment.complete(status, reason);
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment exhaustPolling(Payment payment) {
        payment.exhaustPolling();
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment recordPolling(Payment payment) {
        payment.recordPolling();
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment getByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다."));
    }

    @Transactional(readOnly = true)
    public boolean hasSuccessPayment(Long orderId) {
        return paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS);
    }

    @Transactional(readOnly = true)
    public List<Payment> findAllPendingOrInProgress() {
        return paymentRepository.findAllByStatusIn(List.of(PaymentStatus.CREATED, PaymentStatus.IN_PROGRESS));
    }
}
