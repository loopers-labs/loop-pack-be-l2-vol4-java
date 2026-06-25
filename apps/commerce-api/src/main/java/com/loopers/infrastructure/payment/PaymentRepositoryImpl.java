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
    public Optional<PaymentModel> findByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(orderId);
    }

    @Override
    public Optional<PaymentModel> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKeyAndDeletedAtIsNull(transactionKey);
    }

    @Override
    public List<PaymentModel> findAllByStatus(PaymentStatus status) {
        return paymentJpaRepository.findAllByStatusAndDeletedAtIsNull(status);
    }

    @Override
    public List<PaymentModel> findKeylessPendingBefore(ZonedDateTime cutoff) {
        return paymentJpaRepository.findAllByStatusAndTransactionKeyIsNullAndCreatedAtBeforeAndDeletedAtIsNull(PaymentStatus.PENDING, cutoff);
    }

    @Override
    public List<PaymentModel> findStuckPending(ZonedDateTime cutoff) {
        return paymentJpaRepository.findAllByStatusAndCreatedAtBeforeAndDeletedAtIsNull(PaymentStatus.PENDING, cutoff);
    }

    @Override
    public List<PaymentModel> findSuccessfulSince(ZonedDateTime since) {
        return paymentJpaRepository.findAllByStatusAndUpdatedAtAfterAndDeletedAtIsNull(PaymentStatus.SUCCESS, since);
    }
}
