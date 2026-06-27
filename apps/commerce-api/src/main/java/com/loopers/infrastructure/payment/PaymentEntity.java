package com.loopers.infrastructure.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Entity(name = "Payment")
@Table(name = "payments")
public class PaymentEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "transaction_key", unique = true)
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String reason;

    @Column(name = "polling_count", nullable = false)
    private int pollingCount;

    @Column(name = "last_polled_at")
    private ZonedDateTime lastPolledAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    protected PaymentEntity() {}

    public PaymentEntity(Long userId, Long orderId, CardType cardType, String cardNo, Long amount) {
        this.userId = userId;
        this.orderId = orderId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.CREATED;
        this.pollingCount = 0;
    }

    public Payment toDomain() {
        return new Payment(getId(), userId, orderId, transactionKey, cardType, cardNo, amount,
            status, reason, pollingCount, lastPolledAt, completedAt,
            getCreatedAt(), getUpdatedAt(), getDeletedAt());
    }

    public void updateFrom(Payment domain) {
        this.transactionKey = domain.getTransactionKey();
        this.status = domain.getStatus();
        this.reason = domain.getReason();
        this.pollingCount = domain.getPollingCount();
        this.lastPolledAt = domain.getLastPolledAt();
        this.completedAt = domain.getCompletedAt();
    }
}