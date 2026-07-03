package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.model.PaymentStatus;
import com.loopers.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return paymentJpaRepository.findById(id);
    }

    @Override
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey);
    }

    @Override
    public boolean existsActiveByOrderId(Long orderId) {
        return paymentJpaRepository.existsByOrderIdAndStatusIn(
            orderId, List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS));
    }

    @Override
    public List<Payment> findPendingWithTransactionKey() {
        return paymentJpaRepository.findByStatusAndTransactionKeyIsNotNull(PaymentStatus.PENDING);
    }

    @Override
    public List<Payment> findPendingWithoutTransactionKey() {
        return paymentJpaRepository.findByStatusAndTransactionKeyIsNull(PaymentStatus.PENDING);
    }
}
