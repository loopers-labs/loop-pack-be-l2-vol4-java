package com.loopers.domain.payment;

import java.time.ZonedDateTime;

import com.loopers.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "payments",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_order_id", columnNames = "order_id"),
        @UniqueConstraint(name = "uk_payments_transaction_key", columnNames = "transaction_key")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentModel extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    @Embedded
    private CardNo cardNo;

    @Column(name = "transaction_key", length = 100)
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "reason")
    private String reason;

    @Column(name = "requested_at", nullable = false)
    private ZonedDateTime requestedAt;

    @Builder
    private PaymentModel(Long orderId, Long userId, int amount, CardType cardType, String rawCardNo, ZonedDateTime requestedAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.cardType = cardType;
        this.cardNo = CardNo.from(rawCardNo);
        this.status = PaymentStatus.PENDING;
        this.requestedAt = requestedAt;
    }

    public void recordTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }

    public void confirm(PaymentStatus result, String reason) {
        if (result == PaymentStatus.SUCCESS) {
            succeed();
        } else if (result == PaymentStatus.FAILED) {
            fail(reason);
        }
    }

    public void succeed() {
        if (isTerminal()) {
            return;
        }

        this.status = PaymentStatus.SUCCESS;
    }

    public void fail(String reason) {
        if (isTerminal()) {
            return;
        }

        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }

    public boolean isPending() {
        return this.status == PaymentStatus.PENDING;
    }

    public boolean isSuccess() {
        return this.status == PaymentStatus.SUCCESS;
    }

    public boolean isFailed() {
        return this.status == PaymentStatus.FAILED;
    }

    public boolean isTerminal() {
        return isSuccess() || isFailed();
    }
}
