package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    /**
     * 순수 도메인 ↔ JPA 엔티티 경계. (order 패키지와 동일 패턴)
     * - 신규(id == null): 매퍼로 엔티티를 만들어 INSERT.
     * - 기존(id != null): managed 엔티티를 로드해 가변 상태(거래키/상태/사유)만 복사 → dirty checking으로 UPDATE.
     */
    @Override
    public PaymentModel save(PaymentModel payment) {
        if (payment.getId() == null) {
            PaymentEntity saved = paymentJpaRepository.save(PaymentEntityMapper.toEntity(payment));
            return PaymentEntityMapper.toDomain(saved);
        }
        PaymentEntity entity = paymentJpaRepository.findById(payment.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + payment.getId() + "] 결제를 찾을 수 없습니다."));
        entity.applyState(payment.getTransactionKey(), payment.getStatus(), payment.getReason());
        return PaymentEntityMapper.toDomain(paymentJpaRepository.save(entity));
    }

    @Override
    public Optional<PaymentModel> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey).map(PaymentEntityMapper::toDomain);
    }

    @Override
    public Optional<PaymentModel> findByTransactionKeyForUpdate(String transactionKey) {
        return paymentJpaRepository.findByTransactionKeyForUpdate(transactionKey).map(PaymentEntityMapper::toDomain);
    }

    @Override
    public List<PaymentModel> findByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderId(orderId).stream()
                .map(PaymentEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<PaymentModel> findByStatus(PaymentStatus status, int page, int size) {
        return paymentJpaRepository.findByStatusOrderByIdAsc(status, PageRequest.of(page, size)).stream()
                .map(PaymentEntityMapper::toDomain)
                .toList();
    }
}
