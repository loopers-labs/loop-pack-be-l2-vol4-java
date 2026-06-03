package com.loopers.infrastructure.payment.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.payment.Payment;
import com.loopers.domain.payment.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "payment",
    uniqueConstraints = @UniqueConstraint(name = "uk_payment_order_id", columnNames = "order_id")
)
public class PaymentJpaEntity extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Column(name = "failure_reason")
    private String failureReason;

    protected PaymentJpaEntity() {}

    private PaymentJpaEntity(
        Long orderId,
        Long amount,
        PaymentStatus status,
        String transactionKey,
        String failureReason
    ) {
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.transactionKey = transactionKey;
        this.failureReason = failureReason;
    }

    public static PaymentJpaEntity from(Payment payment) {
        return new PaymentJpaEntity(
            payment.getOrderId(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getTransactionKey(),
            payment.getFailureReason()
        );
    }

    public Payment toDomain() {
        return Payment.reconstruct(
            getId(),
            orderId,
            amount,
            status,
            transactionKey,
            failureReason,
            getCreatedAt(),
            getUpdatedAt(),
            getDeletedAt()
        );
    }

    public void apply(Payment payment) {
        this.orderId = payment.getOrderId();
        this.amount = payment.getAmount();
        this.status = payment.getStatus();
        this.transactionKey = payment.getTransactionKey();
        this.failureReason = payment.getFailureReason();
    }
}
