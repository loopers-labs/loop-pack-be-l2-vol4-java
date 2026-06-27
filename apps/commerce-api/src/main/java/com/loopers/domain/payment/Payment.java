package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.money.Money;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PG 결제 시도 한 건. Order 애그리거트와는 분리되어 orderId(참조)만 들고 독립적으로 산다.
 * 한 주문에 결제 시도가 여러 번일 수 있으므로 Order : Payment = 1 : N 이다.
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    // PG가 발급한 거래 식별자. 콜백/폴링이 이 값으로 결제를 찾는다. 폴백(요청 미확정) 시 null 가능.
    @Column(length = 50)
    private String transactionKey;

    @Embedded
    @AttributeOverride(name = "amount",
        column = @Column(name = "amount", nullable = false))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 200)
    private String reason;

    public Payment(Long orderId, Long userId, String transactionKey, Money amount, PaymentStatus status, String reason) {
        validate(orderId, userId, amount, status);
        this.orderId = orderId;
        this.userId = userId;
        this.transactionKey = transactionKey;
        this.amount = amount;
        this.status = status;
        this.reason = reason;
    }

    private static void validate(Long orderId, Long userId, Money amount, PaymentStatus status) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제는 주문에 속해야 합니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제는 사용자에 속해야 합니다.");
        }
        if (amount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 필수입니다.");
        }
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 상태는 필수입니다.");
        }
    }

    /** 결제 성공 확정. 결제 대기(PENDING) 상태에서만 가능 — 콜백/폴링 중복에도 한 번만 전이된다. */
    public void markSuccess() {
        requirePending();
        this.status = PaymentStatus.SUCCESS;
    }

    /** 결제 실패 확정. */
    public void markFailed(String reason) {
        requirePending();
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }

    private void requirePending() {
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "결제 대기 상태의 결제만 확정할 수 있습니다. 현재 상태: " + this.status);
        }
    }
}
