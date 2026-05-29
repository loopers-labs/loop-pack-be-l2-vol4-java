package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.vo.Money;
import com.loopers.domain.payment.enums.PaymentStatus;
import com.loopers.support.Guard;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
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

    public PaymentModel(Long orderId, Money amount) {
        Guard.notNull(orderId, "주문 ID는 필수입니다.");
        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public void approve() {
        validatePending();
        this.status = PaymentStatus.APPROVED;
    }

    public void fail() {
        validatePending();
        this.status = PaymentStatus.FAILED;
    }

    public void expire() {
        validatePending();
        this.status = PaymentStatus.EXPIRED;
    }

    private void validatePending() {
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "결제 대기 상태에서만 상태를 변경할 수 있습니다. 현재 상태: " + this.status.getDescription());
        }
    }

    public Long getOrderId() { return orderId; }

    public Money getAmount() { return amount; }

    public PaymentStatus getStatus() { return status; }
}
