package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "payments")
public class PaymentModel extends BaseEntity {

    @Column(name = "order_id", unique = true, nullable = false)
    private Long orderId;

    @Column(name = "pg_transaction_id")
    private String pgTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false))
    private Money amount;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "requested_at", nullable = false)
    private ZonedDateTime requestedAt;

    @Column(name = "responded_at")
    private ZonedDateTime respondedAt;

    protected PaymentModel() {}

    public PaymentModel(Long orderId, Long amount) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "orderId는 필수입니다.");
        }
        if (amount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 필수입니다.");
        }
        this.orderId = orderId;
        this.amount = Money.of(amount);   // Money 내부에서 음수 검증
        this.status = PaymentStatus.REQUESTED;
        this.requestedAt = ZonedDateTime.now();
    }

    /**
     * PG 즉시 응답으로 받은 TID를 저장한다. 상태는 REQUESTED 유지 — 콜백 대기 중.
     *
     * <p>TID 는 콜백 미수신 시 조회 키 확보용이다. 콜백이 TID 저장보다 먼저 도착해 이미
     * 종결(SUCCESS/FAILED)된 경우엔 저장을 강행할 이유가 없으므로 <strong>예외 대신 조용히 무시</strong>한다.
     * (예외를 던지면 실제로 성공한 결제가 호출자까지 전파돼 오류로 응답되는 경합이 생긴다.)
     */
    public void storePendingTransactionKey(String pgTransactionId) {
        if (this.status != PaymentStatus.REQUESTED) {
            return;
        }
        this.pgTransactionId = pgTransactionId;
    }

    public void markSuccess(String pgTransactionId) {
        if (this.status != PaymentStatus.REQUESTED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "REQUESTED 상태의 결제만 성공 처리할 수 있습니다.");
        }
        this.status = PaymentStatus.SUCCESS;
        this.pgTransactionId = pgTransactionId;
        this.respondedAt = ZonedDateTime.now();
    }

    public void markFailed(String reason) {
        if (this.status != PaymentStatus.REQUESTED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "REQUESTED 상태의 결제만 실패 처리할 수 있습니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.respondedAt = ZonedDateTime.now();
    }

    public boolean isSuccess() {
        return this.status == PaymentStatus.SUCCESS;
    }

    public boolean isTerminal() {
        return this.status == PaymentStatus.SUCCESS || this.status == PaymentStatus.FAILED;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getPgTransactionId() {
        return pgTransactionId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    /** 금액 (DTO/응답용 — Long). 도메인 내부에서는 {@link Money} 로 캡슐화되어 있다. */
    public Long getAmount() {
        return amount.getAmount();
    }

    public String getFailureReason() {
        return failureReason;
    }

    public ZonedDateTime getRequestedAt() {
        return requestedAt;
    }

    public ZonedDateTime getRespondedAt() {
        return respondedAt;
    }
}
