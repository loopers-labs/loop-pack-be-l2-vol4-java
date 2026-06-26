package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class PaymentModel {

    private Long id;
    private Long userId;
    private Long orderId;
    private CardType cardType;
    private String cardNo;
    private Long amount;
    private String transactionKey;
    private PaymentStatus status;
    private String reason;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    protected PaymentModel() {}

    public PaymentModel(Long id, Long userId, Long orderId, CardType cardType, String cardNo, Long amount,
                        String transactionKey, PaymentStatus status, String reason,
                        ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 null일 수 없습니다.");
        }
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "orderId는 null일 수 없습니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 null일 수 없습니다.");
        }
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 null이거나 비어 있을 수 없습니다.");
        }
        if (amount == null || amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 상태는 null일 수 없습니다.");
        }
        this.id = id;
        this.userId = userId;
        this.orderId = orderId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.transactionKey = transactionKey;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PaymentModel create(Long userId, Long orderId, CardType cardType, String cardNo, Long amount) {
        return new PaymentModel(null, userId, orderId, cardType, cardNo, amount, null, PaymentStatus.PENDING, null, null, null);
    }

    /** PG 에 결제 요청을 보내야 하는 상태인가? (접수 대기 + 아직 거래키 없음) */
    public boolean needsPgRequest() {
        return status == PaymentStatus.PENDING && transactionKey == null;
    }

    /** 멱등성: 이 결제건이 새 요청을 막고 재사용 가능한가? (진행중이거나 성공) */
    public boolean isReusable() {
        return status == PaymentStatus.PENDING || status == PaymentStatus.SUCCESS;
    }

    /** 아직 결과가 확정되지 않았는가? (결과 반영의 멱등성 가드) */
    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    /** PG 가 거래키를 발급(요청 접수 성공)했을 때. 상태는 여전히 PENDING(결과 대기). */
    public void attachTransactionKey(String transactionKey) {
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "거래키는 null이거나 비어 있을 수 없습니다.");
        }
        this.transactionKey = transactionKey;
    }

    public void markSuccess() {
        this.status = PaymentStatus.SUCCESS;
        this.reason = null;
    }

    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getOrderId() { return orderId; }
    public CardType getCardType() { return cardType; }
    public String getCardNo() { return cardNo; }
    public Long getAmount() { return amount; }
    public String getTransactionKey() { return transactionKey; }
    public PaymentStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}
