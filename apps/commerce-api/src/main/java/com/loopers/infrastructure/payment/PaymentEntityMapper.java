package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;

/**
 * PaymentModel(순수 도메인) ↔ PaymentEntity(JPA) 변환기. 도메인과 영속 경계 사이의 번역만 담당한다.
 */
public final class PaymentEntityMapper {

    private PaymentEntityMapper() {}

    public static PaymentEntity toEntity(PaymentModel payment) {
        return new PaymentEntity(
                payment.getOrderId(),
                payment.getUserId(),
                payment.getCardType(),
                payment.getCardNo(),
                payment.getAmount(),
                payment.getTransactionKey(),
                payment.getStatus(),
                payment.getReason()
        );
    }

    public static PaymentModel toDomain(PaymentEntity entity) {
        return PaymentModel.reconstitute(
                entity.getId(),
                entity.getOrderId(),
                entity.getUserId(),
                entity.getCardType(),
                entity.getCardNo(),
                entity.getAmount(),
                entity.getTransactionKey(),
                entity.getStatus(),
                entity.getReason(),
                entity.getCreatedAt()
        );
    }
}
