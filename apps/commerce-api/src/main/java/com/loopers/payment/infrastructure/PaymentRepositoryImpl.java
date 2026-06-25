package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentRepository;
import com.loopers.payment.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentRepositoryImpl implements PaymentRepository {

    private static final List<PaymentStatus> ACTIVE_STATUSES = List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS);

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
    public Optional<Payment> findActiveByOrderNumber(String orderNumber) {
        return paymentJpaRepository.findFirstByOrderNumberAndStatusInOrderByIdDesc(orderNumber, ACTIVE_STATUSES);
    }

    @Override
    public List<Payment> findStalePendingWithKey(ZonedDateTime before) {
        return paymentJpaRepository.findByStatusAndTransactionKeyIsNotNullAndCreatedAtBefore(PaymentStatus.PENDING, before);
    }

    @Override
    public List<Payment> findStalePendingWithoutKey(ZonedDateTime before) {
        return paymentJpaRepository.findByStatusAndTransactionKeyIsNullAndCreatedAtBefore(PaymentStatus.PENDING, before);
    }
}
