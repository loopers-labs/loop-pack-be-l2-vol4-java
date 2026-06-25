package com.loopers.payment.domain;

import com.loopers.common.domain.Money;
import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "payments",
        // 한 주문번호엔 결제 1건만 — 활성 결제 가드(read-then-write)의 동시성 구멍을 막는 최후의 backstop.
        uniqueConstraints = @UniqueConstraint(name = "uk_payments_order_number", columnNames = "order_number"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

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

    private Payment(Long userId, String orderNumber, Money amount) {
        this.userId = userId;
        this.orderNumber = orderNumber;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment create(Long userId, String orderNumber, Money amount) {
        return new Payment(userId, orderNumber, amount);
    }

    public void assignTransaction(String transactionKey, PgProvider pgProvider) {
        this.transactionKey = transactionKey;
        this.pgProvider = pgProvider;
    }

    /**
     * 콜백/보정 통보가 이 결제와 일치하는지 검증한다(서명이 없는 PG라 orderNumber·amount 일치로 진위를 확인).
     */
    public void verifyCallback(String orderNumber, long amount) {
        if (!this.orderNumber.equals(orderNumber) || this.amount.value() != amount) {
            throw new CoreException(ErrorType.BAD_REQUEST, PaymentErrorCode.PAYMENT_CALLBACK_INVALID);
        }
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

    public void markAbandoned(String reason) {
        if (isTerminal()) {
            return;
        }
        this.status = PaymentStatus.ABANDONED;
        this.reason = reason;
    }

    public boolean isTerminal() {
        return status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED || status == PaymentStatus.ABANDONED;
    }
}
