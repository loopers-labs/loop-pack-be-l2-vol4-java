package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
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
    public Optional<PaymentModel> findActiveByOrderId(Long orderId) {
        return paymentJpaRepository.findActiveByOrderId(orderId).stream().findFirst();
    }

    @Override
    public int transitionToPaid(Long id, String transactionKey) {
        return paymentJpaRepository.transitionToPaid(id, transactionKey, ZonedDateTime.now());
    }

    @Override
    public int transitionToFailed(Long id, String reason) {
        return paymentJpaRepository.transitionToFailed(id, reason, ZonedDateTime.now());
    }

    @Override
    public int transitionToUnknown(Long id) {
        return paymentJpaRepository.transitionToUnknown(id, ZonedDateTime.now());
    }

    @Override
    public List<PaymentModel> findPendingOlderThan(ZonedDateTime threshold) {
        return paymentJpaRepository.findAllByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold);
    }
}
