package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.Duration;
import java.time.ZonedDateTime;

public class Payment {

    private Long id;
    private String userLoginId;
    private Long orderId;
    private PaymentCardType cardType;
    private String cardNo;
    private Long amount;
    private PaymentStatus status;
    private PaymentPendingReason pendingReason;
    private String transactionKey;
    private String reason;
    private ZonedDateTime createdAt;

    public Payment(String userLoginId, Long orderId, PaymentCardType cardType, String cardNo, Long amount) {
        this(null, userLoginId, orderId, cardType, cardNo, amount, PaymentStatus.PENDING, null, null, null, null);
    }

    private Payment(
        Long id,
        String userLoginId,
        Long orderId,
        PaymentCardType cardType,
        String cardNo,
        Long amount,
        PaymentStatus status,
        PaymentPendingReason pendingReason,
        String transactionKey,
        String reason,
        ZonedDateTime createdAt
    ) {
        validate(userLoginId, orderId, cardType, cardNo, amount, status);
        this.id = id;
        this.userLoginId = userLoginId;
        this.orderId = orderId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = status;
        this.pendingReason = pendingReason;
        this.transactionKey = transactionKey;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public static Payment reconstruct(
        Long id,
        String userLoginId,
        Long orderId,
        PaymentCardType cardType,
        String cardNo,
        Long amount,
        PaymentStatus status,
        PaymentPendingReason pendingReason,
        String transactionKey,
        String reason,
        ZonedDateTime createdAt
    ) {
        return new Payment(
            id,
            userLoginId,
            orderId,
            cardType,
            cardNo,
            amount,
            status,
            pendingReason,
            transactionKey,
            reason,
            createdAt
        );
    }

    public void applyGatewayResult(PaymentGatewayResult result) {
        if (result == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 처리 결과는 비어있을 수 없습니다.");
        }
        if (status != PaymentStatus.PENDING) {
            return;
        }
        if (result.transactionKey() != null && !result.transactionKey().isBlank()) {
            this.transactionKey = result.transactionKey();
        }
        this.pendingReason = result.pendingReason();
        this.reason = result.reason();

        if (result.status() == PaymentGatewayStatus.SUCCESS) {
            this.status = PaymentStatus.PAID;
            this.pendingReason = null;
        }
        if (result.status() == PaymentGatewayStatus.FAILED) {
            this.status = PaymentStatus.FAILED;
            this.pendingReason = null;
        }
    }

    public void failIfLookupEmptyGracePeriodElapsed(ZonedDateTime now, Duration gracePeriod) {
        if (now == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 시간은 비어있을 수 없습니다.");
        }
        if (gracePeriod == null || gracePeriod.isNegative()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 실패 유예시간은 0 이상이어야 합니다.");
        }
        if (status != PaymentStatus.PENDING || pendingReason != PaymentPendingReason.PG_LOOKUP_EMPTY) {
            return;
        }
        if (createdAt == null || createdAt.plus(gracePeriod).isAfter(now)) {
            return;
        }
        this.status = PaymentStatus.FAILED;
        this.pendingReason = null;
        this.reason = "PG 거래가 확인되지 않아 결제를 실패 처리했습니다.";
    }

    public Long getId() {
        return id;
    }

    public String getUserLoginId() {
        return userLoginId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public PaymentCardType getCardType() {
        return cardType;
    }

    public String getCardNo() {
        return cardNo;
    }

    public Long getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentPendingReason getPendingReason() {
        return pendingReason;
    }

    public String getTransactionKey() {
        return transactionKey;
    }

    public String getReason() {
        return reason;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    private void validate(
        String userLoginId,
        Long orderId,
        PaymentCardType cardType,
        String cardNo,
        Long amount,
        PaymentStatus status
    ) {
        if (userLoginId == null || userLoginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 로그인 ID는 비어있을 수 없습니다.");
        }
        if (orderId == null || orderId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 양수여야 합니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 비어있을 수 없습니다.");
        }
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 비어있을 수 없습니다.");
        }
        if (amount == null || amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 양수여야 합니다.");
        }
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 상태는 비어있을 수 없습니다.");
        }
    }
}
