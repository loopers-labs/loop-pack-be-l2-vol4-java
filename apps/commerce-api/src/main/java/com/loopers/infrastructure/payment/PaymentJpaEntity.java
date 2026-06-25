package com.loopers.infrastructure.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentCardType;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payments_user_order", columnList = "user_login_id, order_id", unique = true),
        @Index(name = "idx_payments_transaction_key", columnList = "transaction_key")
    }
)
public class PaymentJpaEntity extends BaseEntity {

    @Column(name = "user_login_id", nullable = false)
    private String userLoginId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private PaymentCardType cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Column
    private String reason;

    protected PaymentJpaEntity() {
    }

    private PaymentJpaEntity(
        String userLoginId,
        Long orderId,
        PaymentCardType cardType,
        String cardNo,
        Long amount,
        PaymentStatus status,
        String transactionKey,
        String reason
    ) {
        this.userLoginId = userLoginId;
        this.orderId = orderId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = status;
        this.transactionKey = transactionKey;
        this.reason = reason;
    }

    public static PaymentJpaEntity from(Payment payment) {
        return new PaymentJpaEntity(
            payment.getUserLoginId(),
            payment.getOrderId(),
            payment.getCardType(),
            payment.getCardNo(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getTransactionKey(),
            payment.getReason()
        );
    }

    public Payment toDomain() {
        return Payment.reconstruct(
            getId(),
            userLoginId,
            orderId,
            cardType,
            cardNo,
            amount,
            status,
            transactionKey,
            reason,
            getCreatedAt()
        );
    }

    public void update(Payment payment) {
        this.userLoginId = payment.getUserLoginId();
        this.orderId = payment.getOrderId();
        this.cardType = payment.getCardType();
        this.cardNo = payment.getCardNo();
        this.amount = payment.getAmount();
        this.status = payment.getStatus();
        this.transactionKey = payment.getTransactionKey();
        this.reason = payment.getReason();
    }
}
