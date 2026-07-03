package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    @Override
    public PaymentModel save(PaymentModel payment) {
        return jpaRepository.save(payment);
    }

    @Override
    public Optional<PaymentModel> find(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<PaymentModel> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId);
    }

    @Override
    public Optional<PaymentModel> findByTransactionKey(String transactionKey) {
        return jpaRepository.findByTransactionKey(transactionKey);
    }

    @Override
    public List<PaymentModel> findRecoverable(ZonedDateTime requestedBefore, int limit) {
        return jpaRepository.findRecoverable(requestedBefore, PageRequest.of(0, limit));
    }
}
