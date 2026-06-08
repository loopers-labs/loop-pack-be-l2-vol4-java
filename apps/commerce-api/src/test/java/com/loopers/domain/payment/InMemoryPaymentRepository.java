package com.loopers.domain.payment;

import com.loopers.domain.payment.enums.PaymentStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class InMemoryPaymentRepository implements PaymentRepository {

    private final List<PaymentModel> store = new ArrayList<>();

    @Override
    public PaymentModel save(PaymentModel payment) {
        store.add(payment);
        return payment;
    }

    @Override
    public Optional<PaymentModel> findById(Long id) {
        return store.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst();
    }

    @Override
    public List<PaymentModel> findAllByOrderId(Long orderId) {
        return store.stream()
                .filter(p -> p.getOrderId().equals(orderId))
                .toList();
    }

    @Override
    public boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status) {
        return store.stream()
                .anyMatch(p -> p.getOrderId().equals(orderId) && p.getStatus() == status);
    }
}