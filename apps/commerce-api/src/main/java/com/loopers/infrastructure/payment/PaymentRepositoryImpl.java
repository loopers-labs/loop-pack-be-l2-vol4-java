package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentRepositoryImpl implements PaymentRepository {
    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey);
    }

    @Override
    public List<Payment> findStalePending(ZonedDateTime createdBefore) {
        return paymentJpaRepository.findByStatusAndTransactionKeyIsNotNullAndCreatedAtBefore(
            PaymentStatus.PENDING, createdBefore);
    }
}
