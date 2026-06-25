package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentRepository;
import com.loopers.payment.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
    public Payment saveAndFlush(Payment payment) {
        return paymentJpaRepository.saveAndFlush(payment);
    }

    @Override
    public Optional<Payment> findById(Long paymentId) {
        return paymentJpaRepository.findById(paymentId);
    }

    @Override
    public Optional<Payment> findByPgTransactionKey(String pgTransactionKey) {
        return paymentJpaRepository.findByPgTransactionKey(pgTransactionKey);
    }

    @Override
    public Optional<Payment> findLatestByOrderId(Long orderId) {
        return paymentJpaRepository.findFirstByOrderIdOrderByIdDesc(orderId);
    }

    @Override
    public List<Payment> findRecoverablePayments(
        ZonedDateTime now,
        ZonedDateTime requestingDeadline,
        ZonedDateTime pendingDeadline,
        int limit
    ) {
        return paymentJpaRepository.findRecoverablePayments(
            now,
            PaymentStatus.REQUESTING,
            requestingDeadline,
            PaymentStatus.PENDING,
            pendingDeadline,
            PaymentStatus.UNKNOWN,
            PageRequest.of(0, limit)
        );
    }
}
