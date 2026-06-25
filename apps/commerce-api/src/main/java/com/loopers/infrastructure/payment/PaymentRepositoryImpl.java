package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.application.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
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
    public java.util.List<PaymentModel> findAllByStatusAndCreatedAtBefore(com.loopers.domain.payment.PaymentStatus status, java.time.ZonedDateTime time) {
        return paymentJpaRepository.findAllByStatusAndCreatedAtBefore(status, time);
    }
}
