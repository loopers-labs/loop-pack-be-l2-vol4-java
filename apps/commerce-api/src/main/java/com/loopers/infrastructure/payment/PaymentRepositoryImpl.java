package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public PaymentModel save(PaymentModel payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<PaymentModel> findById(Long id) {
        return paymentJpaRepository.findById(id);
    }

    @Override
    public Optional<PaymentModel> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey);
    }

    @Override
    public List<PaymentModel> findStuckPending(ZonedDateTime threshold) {
        return paymentJpaRepository.findByStatusAndTransactionKeyIsNotNullAndCreatedAtBefore(
            PaymentStatus.PENDING, threshold);
    }

    @Override
    public List<PaymentModel> findStuckPendingWithoutKey(ZonedDateTime threshold) {
        return paymentJpaRepository.findByStatusAndTransactionKeyIsNullAndCreatedAtBefore(
            PaymentStatus.PENDING, threshold);
    }
}
