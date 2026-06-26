package com.loopers.domain.payment;

import com.loopers.domain.BaseDomain;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
public class Payment extends BaseDomain {

    private final Long userId;
    private final Long orderId;
    private String transactionKey;
    private final CardType cardType;
    private final String cardNo;
    private final Long amount;
    private PaymentStatus status;
    private String reason;
    private int pollingCount;
    private ZonedDateTime lastPolledAt;
    private ZonedDateTime completedAt;

    public Payment(Long userId, Long orderId, CardType cardType, String cardNo, Long amount) {
        this.userId = userId;
        this.orderId = orderId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.CREATED;
        this.pollingCount = 0;
    }

    public Payment(Long id, Long userId, Long orderId, String transactionKey, CardType cardType, String cardNo, Long amount,
                   PaymentStatus status, String reason, int pollingCount, ZonedDateTime lastPolledAt,
                   ZonedDateTime completedAt, ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        this.id = id;
        this.userId = userId;
        this.orderId = orderId;
        this.transactionKey = transactionKey;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = status;
        this.reason = reason;
        this.pollingCount = pollingCount;
        this.lastPolledAt = lastPolledAt;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public void markInProgress(String transactionKey) {
        if (this.status != PaymentStatus.CREATED) {
            throw new CoreException(ErrorType.CONFLICT, "CREATED 상태의 결제만 처리 시작할 수 있습니다.");
        }
        this.transactionKey = transactionKey;
        this.status = PaymentStatus.IN_PROGRESS;
    }

    public void complete(PaymentStatus status, String reason) {
        if (this.status != PaymentStatus.IN_PROGRESS && this.status != PaymentStatus.POLLING_EXHAUSTED) {
            throw new CoreException(ErrorType.CONFLICT, "IN_PROGRESS 또는 POLLING_EXHAUSTED 상태의 결제만 완료 처리할 수 있습니다.");
        }
        this.status = status;
        this.reason = reason;
        this.completedAt = ZonedDateTime.now();
    }

    public void exhaustPolling() {
        if (this.status != PaymentStatus.CREATED && this.status != PaymentStatus.IN_PROGRESS) {
            throw new CoreException(ErrorType.CONFLICT, "CREATED 또는 IN_PROGRESS 상태의 결제만 폴링 소진 처리할 수 있습니다.");
        }
        this.status = PaymentStatus.POLLING_EXHAUSTED;
    }

    public void recordPolling() {
        if (this.status != PaymentStatus.CREATED && this.status != PaymentStatus.IN_PROGRESS) {
            throw new CoreException(ErrorType.CONFLICT, "CREATED 또는 IN_PROGRESS 상태의 결제만 폴링 횟수를 기록할 수 있습니다.");
        }
        this.pollingCount++;
        this.lastPolledAt = ZonedDateTime.now();
    }
}
