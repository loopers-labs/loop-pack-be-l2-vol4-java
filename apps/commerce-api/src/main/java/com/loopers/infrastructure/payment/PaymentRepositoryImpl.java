package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        if (payment.getId() == null) {
            return paymentJpaRepository.save(
                new PaymentEntity(payment.getOrderId(), payment.getCardType(), payment.getCardNo(), payment.getAmount())
            ).toDomain();
        }
        PaymentEntity entity = paymentJpaRepository.findById(payment.getId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다."));
        entity.updateFrom(payment);
        return paymentJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return paymentJpaRepository.findById(id).map(PaymentEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey).map(PaymentEntity::toDomain);
    }

    @Override
    public boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status) {
        return paymentJpaRepository.existsByOrderIdAndStatus(orderId, status);
    }

    @Override
    public List<Payment> findAllByStatusIn(List<PaymentStatus> statuses) {
        return paymentJpaRepository.findAllByStatusIn(statuses)
            .stream().map(PaymentEntity::toDomain).toList();
    }
}