package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
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
    public PaymentModel save(PaymentModel payment) {
        if (payment.getId() != null) {
            PaymentEntity entity = paymentJpaRepository.findById(payment.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + payment.getId() + "] 결제를 찾을 수 없습니다."));
            entity.sync(payment);
            return paymentJpaRepository.save(entity).toDomain();
        }
        return paymentJpaRepository.save(PaymentEntity.from(payment)).toDomain();
    }

    @Override
    public Optional<PaymentModel> find(Long id) {
        return paymentJpaRepository.findById(id).map(PaymentEntity::toDomain);
    }

    @Override
    public Optional<PaymentModel> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey).map(PaymentEntity::toDomain);
    }

    @Override
    public List<PaymentModel> findByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderId(orderId).stream().map(PaymentEntity::toDomain).toList();
    }

    @Override
    public List<PaymentModel> findByStatus(PaymentStatus status) {
        return paymentJpaRepository.findByStatus(status).stream().map(PaymentEntity::toDomain).toList();
    }
}
