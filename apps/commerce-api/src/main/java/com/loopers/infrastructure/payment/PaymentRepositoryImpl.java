package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentCreationResult;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentCreationResult create(Payment payment) {
        try {
            Payment savedPayment = paymentJpaRepository.saveAndFlush(PaymentJpaEntity.from(payment)).toDomain();
            return PaymentCreationResult.created(savedPayment);
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicatePayment(exception)) {
                throw exception;
            }
            entityManager.clear();
            return paymentJpaRepository.findByOrderIdAndUserLoginId(payment.getOrderId(), payment.getUserLoginId())
                .map(PaymentJpaEntity::toDomain)
                .map(PaymentCreationResult::existing)
                .orElseThrow(() -> exception);
        }
    }

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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment completeIfPending(Payment payment) {
        if (payment.getId() == null) {
            return save(payment);
        }
        paymentJpaRepository.completeIfPending(
            payment.getId(),
            payment.getStatus(),
            payment.getPendingReason(),
            payment.getTransactionKey(),
            payment.getReason(),
            PaymentStatus.PENDING
        );
        return paymentJpaRepository.findById(payment.getId())
            .orElseThrow()
            .toDomain();
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

    private boolean isDuplicatePayment(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String message = cause == null ? exception.getMessage() : cause.getMessage();
        return message != null
            && message.toLowerCase(Locale.ROOT).contains("idx_payments_user_order");
    }
}
