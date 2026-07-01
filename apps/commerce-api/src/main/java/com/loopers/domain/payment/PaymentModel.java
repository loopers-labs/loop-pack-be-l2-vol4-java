package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "payments")
public class PaymentModel extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, updatable = false, length = 20)
    private CardType cardType;

    @Column(name = "card_no", nullable = false, updatable = false, length = 25)
    private String cardNo;

    @Column(name = "amount", nullable = false, updatable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "pg_transaction_id", length = 50)
    private String pgTransactionId;

    // PG 미도달이면 null, PG 도달 후 실패면 PG 에러 코드 저장
    @Column(name = "failure_code", length = 50)
    private String failureCode;

    protected PaymentModel() {}

    public PaymentModel(Long orderId, Long userId, CardType cardType, String cardNo, int amount) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 필수입니다.");
        }
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다.");
        }
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        this.orderId = orderId;
        this.userId = userId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    /**
     * PG 요청 성공 시 호출. PENDING → IN_PROGRESS 전환 및 트랜잭션 ID 저장.
     * failureCode == null 이면 PG 미도달, 값이 있으면 PG 도달 후 실패로 구분 가능.
     */
    public void startProgress(String pgTransactionId) {
        validateTransitionFrom(PaymentStatus.PENDING);
        if (pgTransactionId == null || pgTransactionId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 트랜잭션 ID는 필수입니다.");
        }
        this.pgTransactionId = pgTransactionId;
        this.status = PaymentStatus.IN_PROGRESS;
    }

    /**
     * PG 요청 실패 또는 콜백 실패 시 호출. failureCode가 null이면 PG 미도달, 값이 있으면 PG 에러 코드.
     */
    public void markFailed(String failureCode) {
        if (this.status != PaymentStatus.PENDING && this.status != PaymentStatus.IN_PROGRESS) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "현재 상태(%s)에서는 실패 처리할 수 없습니다.".formatted(this.status));
        }
        this.failureCode = failureCode;
        this.status = PaymentStatus.FAILED;
    }

    public void markSuccess() {
        validateTransitionFrom(PaymentStatus.IN_PROGRESS);
        this.status = PaymentStatus.SUCCESS;
    }

    public void markAborted() {
        if (this.status != PaymentStatus.PENDING && this.status != PaymentStatus.IN_PROGRESS) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "현재 상태(%s)에서는 강제 종료할 수 없습니다.".formatted(this.status));
        }
        this.status = PaymentStatus.ABORTED;
    }

    /**
     * PG 상태 조회 결과를 기반으로 현재 상태를 복구한다.
     * SUCCESS/FAILED 최종 상태는 변경하지 않는다 (멱등성 보장).
     * PG 응답이 PENDING/IN_PROGRESS면 아직 처리 중이므로 변경 없음.
     */
    public boolean hasPgTransactionRecord() {
        return pgTransactionId != null;
    }

    public void applyPgResult(String transactionKey, String pgStatus, String reason) {
        if (this.status == PaymentStatus.SUCCESS || this.status == PaymentStatus.FAILED) {
            return;
        }
        if (this.pgTransactionId == null && transactionKey != null) {
            this.pgTransactionId = transactionKey;
        }
        if ("SUCCESS".equals(pgStatus)) {
            this.status = PaymentStatus.SUCCESS;
        } else if ("FAILED".equals(pgStatus)) {
            this.failureCode = reason;
            this.status = PaymentStatus.FAILED;
        }
    }

    private void validateTransitionFrom(PaymentStatus expected) {
        if (this.status != expected) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "현재 상태(%s)에서는 전환할 수 없습니다.".formatted(this.status));
        }
    }
}
