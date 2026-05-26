package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public PaymentModel save(PaymentModel payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<PaymentModel> findByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderId(orderId);
    }
}
