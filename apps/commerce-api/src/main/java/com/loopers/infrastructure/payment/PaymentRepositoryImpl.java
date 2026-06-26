package com.loopers.infrastructure.payment;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public PaymentModel save(PaymentModel payment) {
        try {
            return paymentJpaRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 결제가 진행 중이거나 완료된 주문입니다.");
        }
    }

    @Override
    public PaymentModel getById(Long paymentId) {
        return paymentJpaRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제가 존재하지 않습니다."));
    }

    @Override
    public PaymentModel getByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제가 존재하지 않습니다."));
    }

    @Override
    public boolean existsByOrderId(Long orderId) {
        return paymentJpaRepository.existsByOrderId(orderId);
    }

    @Override
    public List<PaymentModel> findPendingRequestedBefore(ZonedDateTime threshold) {
        return paymentJpaRepository.findByStatusAndRequestedAtLessThanEqual(PaymentStatus.PENDING, threshold);
    }

    @Override
    public int confirmIfUnresolved(Long paymentId, PaymentStatus status, String reason) {
        return paymentJpaRepository.confirmIfUnresolved(paymentId, status, reason);
    }
}
