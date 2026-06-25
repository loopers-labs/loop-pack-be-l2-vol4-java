package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.ZonedDateTime;

@Entity
@Table(name = "payments")
@Getter
public class PaymentJpaEntity extends BaseJpaEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    protected PaymentJpaEntity() {}

    PaymentJpaEntity(Long id, Long orderId, Long userId, String transactionKey,
            CardType cardType, String cardNo, Long amount,
            PaymentStatus status, String failureReason, ZonedDateTime deletedAt) {
        super(id, deletedAt);
        this.orderId = orderId;
        this.userId = userId;
        this.transactionKey = transactionKey;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = status;
        this.failureReason = failureReason;
    }
}
