package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity paymentJpaEntity = payment.getId() == null
            ? PaymentJpaEntity.from(payment)
            : paymentJpaRepository.findById(payment.getId())
                .map(existingPayment -> {
                    existingPayment.update(payment);
                    return existingPayment;
                })
                .orElseGet(() -> PaymentJpaEntity.from(payment));

        return paymentJpaRepository.save(paymentJpaEntity).toDomain();
    }

    @Override
    public Optional<Payment> findByIdAndUserLoginId(Long id, String userLoginId) {
        return paymentJpaRepository.findByIdAndUserLoginId(id, userLoginId)
            .map(PaymentJpaEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderIdAndUserLoginId(Long orderId, String userLoginId) {
        return paymentJpaRepository.findByOrderIdAndUserLoginId(orderId, userLoginId)
            .map(PaymentJpaEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderId(orderId)
            .map(PaymentJpaEntity::toDomain);
    }
}
