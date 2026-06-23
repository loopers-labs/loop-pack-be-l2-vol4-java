package com.loopers.domain.payment;

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

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Column(name = "reason")
    private String reason;

    protected PaymentModel() {}

    private PaymentModel(Long userId, Long orderId, Long amount, PaymentStatus status) {
        this.userId = userId;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
    }

    public static PaymentModel createPending(Long userId, Long orderId, Long amount) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID 는 비어있을 수 없습니다.");
        }
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID 는 비어있을 수 없습니다.");
        }
        if (amount == null || amount <= 0) {
            throw new CoreException(ErrorType.INVALID_PAYMENT_AMOUNT, "결제 금액은 0 보다 커야 합니다. [amount = " + amount + "]");
        }
        return new PaymentModel(userId, orderId, amount, PaymentStatus.PENDING);
    }

    /**
     * PG 접수 응답으로 받은 transactionKey 를 부여한다.
     * 이미 키가 있거나 종착 상태이면 재시도/중복 수신에도 안전하도록 멱등하게 무시한다.
     */
    public void assignTransactionKey(String transactionKey) {
        if (status != PaymentStatus.PENDING) {
            return;
        }
        if (this.transactionKey != null) {
            return;
        }
        this.transactionKey = transactionKey;
    }

    /**
     * 결제 성공을 반영한다. 콜백·폴링이 중복 수렴해도 안전하도록 PENDING 에서만 전이하고,
     * 이미 종착(SUCCESS/FAILED)이면 멱등하게 no-op 한다. (FAILED → SUCCESS 역전 금지)
     */
    public void markSuccess(String reason) {
        if (status != PaymentStatus.PENDING) {
            return;
        }
        this.status = PaymentStatus.SUCCESS;
        this.reason = reason;
    }

    /**
     * 결제 실패를 반영한다. PENDING 에서만 전이하며, 이미 종착(SUCCESS/FAILED)이면
     * 멱등하게 no-op 한다. (SUCCESS → FAILED 역전 금지)
     */
    public void markFailed(String reason) {
        if (status != PaymentStatus.PENDING) {
            return;
        }
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getTransactionKey() {
        return transactionKey;
    }

    public String getReason() {
        return reason;
    }
}
