package com.loopers.domain.payment;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 단위 테스트용 인메모리 PaymentRepository.
 * 동시성/실제 인덱스 동작은 통합 테스트(Testcontainers) 에서 검증한다.
 */
public class FakePaymentRepository implements PaymentRepository {

    private final Map<Long, Payment> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public Payment save(Payment payment) {
        if (payment.getId() == null || payment.getId() == 0L) {
            ReflectionTestUtils.setField(payment, "id", sequence.incrementAndGet());
        }
        // BaseEntity 의 createdAt 은 @PrePersist 에서 채워지지만 Fake 환경에선 수동 보강.
        if (payment.getCreatedAt() == null) {
            ReflectionTestUtils.setField(payment, "createdAt", ZonedDateTime.now());
            ReflectionTestUtils.setField(payment, "updatedAt", ZonedDateTime.now());
        }
        store.put(payment.getId(), payment);
        return payment;
    }

    @Override
    public Optional<Payment> find(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        if (transactionKey == null) {
            return Optional.empty();
        }
        return store.values().stream()
            .filter(p -> transactionKey.equals(p.getTransactionKey()))
            .findFirst();
    }

    @Override
    public List<Payment> findActiveByOrderId(Long orderId) {
        List<Payment> result = new ArrayList<>();
        for (Payment p : store.values()) {
            if (p.getOrderId().equals(orderId) && !p.getStatus().isFinal()) {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public List<Payment> findAllByOrderId(Long orderId) {
        List<Payment> result = new ArrayList<>();
        for (Payment p : store.values()) {
            if (p.getOrderId().equals(orderId)) {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public List<Payment> findReconciliationTargets(ZonedDateTime threshold) {
        return collectByStatusAndBefore(threshold);
    }

    @Override
    public List<Payment> findTimeoutTargets(ZonedDateTime threshold) {
        return collectByStatusAndBefore(threshold);
    }

    private List<Payment> collectByStatusAndBefore(ZonedDateTime threshold) {
        List<Payment> result = new ArrayList<>();
        for (Payment p : store.values()) {
            if (p.getStatus().needsReconciliation() && p.getCreatedAt().isBefore(threshold)) {
                result.add(p);
            }
        }
        return result;
    }
}
