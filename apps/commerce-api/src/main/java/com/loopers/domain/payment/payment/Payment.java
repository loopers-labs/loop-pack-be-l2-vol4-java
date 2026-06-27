package com.loopers.domain.payment.payment;

import com.loopers.support.domain.DomainEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class Payment extends DomainEntity {

    private Long orderId;

    private Long amount;

    private PaymentStatus status;

    private String transactionKey;

    private String failureReason;

    public Payment(Long orderId, Long amount) {
        validateOrderId(orderId);
        validateAmount(amount);

        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentStatus.REQUESTED;
    }

    public static Payment reconstruct(
        Long id,
        Long orderId,
        Long amount,
        PaymentStatus status,
        String transactionKey,
        String failureReason,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        Payment payment = new Payment(orderId, amount);
        payment.status = status == null ? payment.status : status;
        payment.transactionKey = transactionKey;
        payment.failureReason = failureReason;
        payment.assignMetadata(id, createdAt, updatedAt, deletedAt);
        return payment;
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

    public boolean isRequested() {
        return status == PaymentStatus.REQUESTED;
    }

    public boolean isProcessing() {
        return status == PaymentStatus.PROCESSING;
    }

    public String getTransactionKey() {
        return transactionKey;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void markProcessing(String transactionKey) {
        ensureRequested();
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 거래 키는 필수입니다.");
        }

        this.status = PaymentStatus.PROCESSING;
        this.transactionKey = transactionKey;
        this.failureReason = null;
    }

    public void markSuccess(String transactionKey) {
        ensureCompletable();
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 거래 키는 필수입니다.");
        }

        this.status = PaymentStatus.SUCCESS;
        this.transactionKey = transactionKey;
        this.failureReason = null;
    }

    public void markFailed(String failureReason) {
        ensureCompletable();
        this.status = PaymentStatus.FAILED;
        this.failureReason = failureReason;
    }

    public void markCanceled(String failureReason) {
        ensureCompletable();
        this.status = PaymentStatus.CANCELED;
        this.failureReason = failureReason;
    }

    private void ensureRequested() {
        if (status != PaymentStatus.REQUESTED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "요청 상태 결제만 변경할 수 있습니다.");
        }
    }

    private void ensureCompletable() {
        if (status != PaymentStatus.REQUESTED && status != PaymentStatus.PROCESSING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "요청 또는 처리 중 상태 결제만 변경할 수 있습니다.");
        }
    }

    private void validateOrderId(Long value) {
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
    }

    private void validateAmount(Long value) {
        if (value == null || value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0 이상이어야 합니다.");
        }
    }
}
