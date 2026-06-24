package com.loopers.payment.domain;

import com.loopers.common.domain.Money;
import com.loopers.domain.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "pg_provider")
    private PgProvider pgProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "amount", nullable = false))
    private Money amount;

    @Column(name = "reason")
    private String reason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private Payment(Long orderId, Money amount) {
        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment create(Long orderId, Money amount) {
        return new Payment(orderId, amount);
    }

    public void assignTransaction(String transactionKey, PgProvider pgProvider) {
        this.transactionKey = transactionKey;
        this.pgProvider = pgProvider;
    }

    public void markSuccess() {
        if (isTerminal()) {
            return;
        }
        this.status = PaymentStatus.SUCCESS;
    }

    public void markFailed(String reason) {
        if (isTerminal()) {
            return;
        }
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }

    public boolean isTerminal() {
        return status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED;
    }
}
