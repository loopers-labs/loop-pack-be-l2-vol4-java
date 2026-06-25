package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentEntity;

public class PaymentMapper {

    public static PaymentJpaEntity toJpaEntity(PaymentEntity payment) {
        return new PaymentJpaEntity(
            payment.getId(),
            payment.getOrderId(),
            payment.getUserId(),
            payment.getTransactionKey(),
            payment.getCardType(),
            payment.getCardNo(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getFailureReason(),
            payment.getDeletedAt()
        );
    }

    public static PaymentEntity toDomain(PaymentJpaEntity jpa) {
        return PaymentEntity.of(
            jpa.getId(),
            jpa.getOrderId(),
            jpa.getUserId(),
            jpa.getTransactionKey(),
            jpa.getCardType(),
            jpa.getCardNo(),
            jpa.getAmount(),
            jpa.getStatus(),
            jpa.getFailureReason(),
            jpa.getCreatedAt(),
            jpa.getUpdatedAt(),
            jpa.getDeletedAt()
        );
    }
}
