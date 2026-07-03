package com.loopers.domain.payment.model;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_order", columnList = "order_id"),
        @Index(name = "idx_payment_transaction_key", columnList = "transaction_key")
    }
)
@Getter
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "transaction_key", unique = true)
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

    @Column(name = "reason")
    private String reason;

    protected Payment() {}

    private Payment(Long orderId, Long memberId, CardType cardType, String cardNo, Long amount) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 필수입니다.");
        }
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다.");
        }
        if (amount == null || amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        this.orderId = orderId;
        this.memberId = memberId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment create(Long orderId, Long memberId, CardType cardType, String cardNo, Long amount) {
        return new Payment(orderId, memberId, cardType, cardNo, amount);
    }

    public void assignTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }

    public void markSuccess(String reason) {
        guardPending();
        this.status = PaymentStatus.SUCCESS;
        this.reason = reason;
    }

    public void markFailed(String reason) {
        guardPending();
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }

    public boolean isPending() {
        return this.status == PaymentStatus.PENDING;
    }

    private void guardPending() {
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "이미 확정된 결제는 변경할 수 없습니다.");
        }
    }
}
