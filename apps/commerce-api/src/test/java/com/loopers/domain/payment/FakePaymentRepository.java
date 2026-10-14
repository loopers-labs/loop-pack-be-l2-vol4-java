package com.loopers.domain.payment;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakePaymentRepository implements PaymentRepository {

    private final Map<Long, PaymentModel> store = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public PaymentModel save(PaymentModel payment) {
        if (payment.getId() == null || payment.getId() == 0L) {
            // UNIQUE(order_id) 위반 시뮬레이션
            for (PaymentModel existing : store.values()) {
                if (existing.getOrderId().equals(payment.getOrderId())) {
                    throw new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate orderId=" + payment.getOrderId());
                }
            }
            assignId(payment, sequence.incrementAndGet());
        }
        store.put(payment.getId(), payment);
        return payment;
    }

    @Override
    public Optional<PaymentModel> find(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<PaymentModel> findByOrderId(Long orderId) {
        return store.values().stream().filter(p -> p.getOrderId().equals(orderId)).findFirst();
    }

    @Override
    public Optional<PaymentModel> findByTransactionKey(String transactionKey) {
        return store.values().stream()
            .filter(p -> transactionKey.equals(p.getTransactionKey()))
            .findFirst();
    }

    @Override
    public List<PaymentModel> findRecoverable(ZonedDateTime requestedBefore, int limit) {
        List<PaymentModel> result = new ArrayList<>();
        for (PaymentModel p : store.values()) {
            if (p.getStatus() == PaymentStatus.TIMEOUT_PENDING
                || p.getStatus() == PaymentStatus.REQUESTED) {
                result.add(p);
            }
            if (result.size() >= limit) break;
        }
        return result;
    }

    private static void assignId(Object entity, Long id) {
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null && !clazz.getSimpleName().equals("BaseEntity")) {
                clazz = clazz.getSuperclass();
            }
            if (clazz == null) throw new IllegalStateException("BaseEntity 를 찾을 수 없습니다.");
            Field idField = clazz.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Fake Repository: ID 주입 실패", e);
        }
    }
}
