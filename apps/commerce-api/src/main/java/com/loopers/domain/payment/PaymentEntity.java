package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;

public class PaymentEntity extends BaseEntity {

    private Long orderId;
    private Long userId;
    private String transactionKey;
    private CardType cardType;
    private String cardNo;
    private Long amount;
    private PaymentStatus status;
    private String failureReason;

    protected PaymentEntity() {}

    public PaymentEntity(Long orderId, Long userId, CardType cardType, String cardNo, Long amount) {
        if (orderId == null) throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        if (userId == null) throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        if (cardType == null) throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 필수입니다.");
        if (cardNo == null || cardNo.isBlank()) throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다.");
        if (amount == null || amount <= 0) throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        this.orderId = orderId;
        this.userId = userId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static PaymentEntity of(Long id, Long orderId, Long userId, String transactionKey,
            CardType cardType, String cardNo, Long amount, PaymentStatus status, String failureReason,
            ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        PaymentEntity entity = new PaymentEntity();
        entity.orderId = orderId;
        entity.userId = userId;
        entity.transactionKey = transactionKey;
        entity.cardType = cardType;
        entity.cardNo = cardNo;
        entity.amount = amount;
        entity.status = status;
        entity.failureReason = failureReason;
        entity.reconstruct(id, createdAt, updatedAt, deletedAt);
        return entity;
    }

    public void registerTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }

    public void approve() {
        if (this.status == PaymentStatus.SUCCESS) return; // 멱등
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 승인 처리할 수 없는 상태입니다.");
        }
        this.status = PaymentStatus.SUCCESS;
    }

    public void fail(String reason) {
        if (this.status == PaymentStatus.FAILED) return; // 멱등
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 실패 처리할 수 없는 상태입니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public Long getOrderId() { return orderId; }
    public Long getUserId() { return userId; }
    public String getTransactionKey() { return transactionKey; }
    public CardType getCardType() { return cardType; }
    public String getCardNo() { return cardNo; }
    public Long getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
}
