package com.loopers.payment.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class PaymentModel extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "transaction_key", nullable = false, unique = true)
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "card_type", nullable = false)
    private String cardType;

    @Column(name = "amount", nullable = false)
    private Long amount;

    protected PaymentModel() {}

    public PaymentModel(Long orderId, String transactionKey, String cardType, Long amount) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "orderId는 비어있을 수 없습니다.");
        }
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "transactionKey는 비어있을 수 없습니다.");
        }
        this.orderId = orderId;
        this.transactionKey = transactionKey;
        this.cardType = cardType;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public void confirm() {
        if (this.status != PaymentStatus.PENDING) return;
        this.status = PaymentStatus.SUCCESS;
    }

    public void fail() {
        if (this.status != PaymentStatus.PENDING) return;
        this.status = PaymentStatus.FAILED;
    }

    public Long getOrderId() { return orderId; }
    public String getTransactionKey() { return transactionKey; }
    public PaymentStatus getStatus() { return status; }
    public String getCardType() { return cardType; }
    public Long getAmount() { return amount; }
}
