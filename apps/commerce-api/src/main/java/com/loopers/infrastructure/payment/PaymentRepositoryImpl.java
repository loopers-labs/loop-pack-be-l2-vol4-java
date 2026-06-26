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

    /** 진행 중 결제로 간주하는 상태 집합. 한 곳에 모아 정책 변경 시 한 줄로 조절. */
    private static final List<PaymentStatus> ACTIVE_STATUSES = List.of(
        PaymentStatus.REQUESTED,
        PaymentStatus.IN_PROGRESS,
        PaymentStatus.UNKNOWN
    );

    /** 폴링/타임아웃 대상 — PG 응답을 기다리는 상태 집합. */
    private static final List<PaymentStatus> RECONCILIATION_STATUSES = List.of(
        PaymentStatus.IN_PROGRESS,
        PaymentStatus.UNKNOWN
    );

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> find(Long id) {
        return paymentJpaRepository.findById(id);
    }

    @Override
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey);
    }

    @Override
    public List<Payment> findActiveByOrderId(Long orderId) {
        return paymentJpaRepository.findActiveByOrderId(orderId, ACTIVE_STATUSES);
    }

    @Override
    public List<Payment> findAllByOrderId(Long orderId) {
        return paymentJpaRepository.findAllByOrderId(orderId);
    }

    @Override
    public List<Payment> findReconciliationTargets(ZonedDateTime threshold) {
        return paymentJpaRepository.findByStatusInAndCreatedAtBefore(RECONCILIATION_STATUSES, threshold);
    }

    @Override
    public List<Payment> findTimeoutTargets(ZonedDateTime threshold) {
        return paymentJpaRepository.findByStatusInAndCreatedAtBefore(RECONCILIATION_STATUSES, threshold);
    }
}
