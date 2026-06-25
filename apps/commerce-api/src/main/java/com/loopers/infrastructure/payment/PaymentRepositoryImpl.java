package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentEntity;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public PaymentEntity save(PaymentEntity payment) {
        return PaymentMapper.toDomain(paymentJpaRepository.save(PaymentMapper.toJpaEntity(payment)));
    }

    @Override
    public Optional<PaymentEntity> findById(Long id) {
        return paymentJpaRepository.findById(id).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<PaymentEntity> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey).map(PaymentMapper::toDomain);
    }

    @Override
    public boolean existsByOrderIdAndStatusIn(Long orderId, PaymentStatus... statuses) {
        return paymentJpaRepository.existsByOrderIdAndStatusIn(orderId, statuses);
    }
}
