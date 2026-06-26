package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.vo.Money;
import com.loopers.domain.payment.enums.PaymentStatus;
import com.loopers.domain.payment.enums.PgTransactionStatus;
import com.loopers.support.Guard;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_status_created_at", columnList = "status, created_at"),
        @Index(name = "idx_payments_order_id_status", columnList = "order_id, status")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentModel extends BaseEntity {

    @Column(nullable = false)
    private Long orderId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "amount", nullable = false))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column
    private String transactionKey;

    public PaymentModel(Long orderId, Money amount) {
        Guard.notNull(orderId, "주문 ID는 필수입니다.");
        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentStatus.REQUEST;
    }

    public void startProcessing(String transactionKey) {
        if (this.status != PaymentStatus.REQUEST) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 요청 상태에서만 진행 처리할 수 있습니다.");
        }
        this.transactionKey = transactionKey;
        this.status = PaymentStatus.PENDING;
    }

    public void applyCallback(PgTransactionStatus pgStatus) {
        if (this.status != PaymentStatus.PENDING) return;
        if (pgStatus == PgTransactionStatus.PENDING) return;
        if (pgStatus.isSuccess()) {
            approve();
        } else {
            fail();
        }
    }

    public void approve() {
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "결제 진행중 상태에서만 승인할 수 있습니다. 현재 상태: " + this.status.getDescription());
        }
        this.status = PaymentStatus.APPROVED;
    }

    public void fail() {
        validateActive();
        this.status = PaymentStatus.FAILED;
    }

    public void expire() {
        validateActive();
        this.status = PaymentStatus.EXPIRED;
    }

    private void validateActive() {
        if (this.status != PaymentStatus.REQUEST && this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "결제 요청 또는 진행중 상태에서만 상태를 변경할 수 있습니다. 현재 상태: " + this.status.getDescription());
        }
    }

    public Long getOrderId() { return orderId; }

    public Money getAmount() { return amount; }

    public PaymentStatus getStatus() { return status; }

    public String getTransactionKey() { return transactionKey; }
}
