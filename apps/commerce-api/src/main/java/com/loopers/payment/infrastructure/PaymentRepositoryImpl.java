package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
}
