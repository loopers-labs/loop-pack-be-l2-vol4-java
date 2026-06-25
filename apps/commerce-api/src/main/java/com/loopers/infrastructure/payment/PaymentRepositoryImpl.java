package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
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
    public Optional<Payment> find(Long id) {
        return paymentJpaRepository.findById(id);
    }

    @Override
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey);
    }

    @Override
    public Optional<Payment> findActiveByOrderId(Long orderId) {
        // 활성 = PENDING 또는 SUCCESS (한 주문에 활성 결제는 하나)
        return paymentJpaRepository.findFirstByOrderIdAndStatusInOrderByIdDesc(
            orderId, List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS));
    }

    @Override
    public List<Payment> findPendingOlderThan(ZonedDateTime threshold) {
        return paymentJpaRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold);
    }
}
