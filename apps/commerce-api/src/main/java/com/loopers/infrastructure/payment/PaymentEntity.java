package com.loopers.infrastructure.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "payment",
    indexes = {
        @Index(name = "idx_payment_order_id", columnList = "order_id"),
        @Index(name = "idx_payment_transaction_key", columnList = "transaction_key"),
        @Index(name = "idx_payment_status", columnList = "status")
    }
)
public class PaymentEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String reason;

    private PaymentEntity(Long userId, Long orderId, CardType cardType, String cardNo, Long amount,
                          String transactionKey, PaymentStatus status, String reason) {
        this.userId = userId;
        this.orderId = orderId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.transactionKey = transactionKey;
        this.status = status;
        this.reason = reason;
    }

    public static PaymentEntity from(PaymentModel model) {
        return new PaymentEntity(
            model.getUserId(),
            model.getOrderId(),
            model.getCardType(),
            model.getCardNo(),
            model.getAmount(),
            model.getTransactionKey(),
            model.getStatus(),
            model.getReason()
        );
    }

    public void sync(PaymentModel model) {
        this.transactionKey = model.getTransactionKey();
        this.status = model.getStatus();
        this.reason = model.getReason();
    }

    public PaymentModel toDomain() {
        return new PaymentModel(
            getId(),
            userId,
            orderId,
            cardType,
            cardNo,
            amount,
            transactionKey,
            status,
            reason,
            getCreatedAt(),
            getUpdatedAt()
        );
    }
}
