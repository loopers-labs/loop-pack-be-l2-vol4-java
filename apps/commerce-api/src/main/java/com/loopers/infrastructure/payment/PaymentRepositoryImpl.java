package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
    public Optional<PaymentModel> findById(Long id) {
        return paymentJpaRepository.findById(id);
    }

    @Override
    public Optional<PaymentModel> findByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderId(orderId);
    }

    @Override
    public Optional<PaymentModel> findByPgTransactionId(String pgTransactionId) {
        return paymentJpaRepository.findByPgTransactionId(pgTransactionId);
    }
}
