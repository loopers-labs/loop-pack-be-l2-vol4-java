package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.Optional;

/**
 * 결제 Aggregate.
 * - 자신의 상태 전이를 스스로 책임진다 (CLAUDE.md §7).
 * - 종료(SUCCESS/FAILED) 이후의 모든 전이는 '조용히 무시'하여
 *   콜백/폴링 중복·경합에도 정합성을 지킨다 (idempotent 상태 가드).
 */
@Entity
@Table(name = "payments")
public class PaymentModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    @Column(name = "card_no", nullable = false, length = 30)
    private String cardNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "transaction_key")        // PG 접수 전엔 null
    private String transactionKey;

    @Column(name = "reason", length = 200)   // PG 처리 사유 (성공/실패), nullable
    private String reason;

    protected PaymentModel() {}

    private PaymentModel(Long userId, Long orderId, Money amount, CardType cardType, String cardNo) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제자는 필수입니다.");
        }
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
        if (amount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 필수입니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 필수입니다.");
        }
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다.");
        }
        this.userId = userId;
        this.orderId = orderId;
        this.amount = amount;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.status = PaymentStatus.PENDING;
    }

    public static PaymentModel create(Long userId, Long orderId, Money amount, CardType cardType, String cardNo) {
        return new PaymentModel(userId, orderId, amount, cardType, cardNo);
    }

    /** PG가 요청을 접수(txKey 발급)했을 때. PENDING → PROCESSING. 종료 상태면 무시. */
    public void markProcessing(String transactionKey) {
        if (status.isFinalized()) {
            return;
        }
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "트랜잭션 키가 비어있습니다.");
        }
        this.transactionKey = transactionKey;
        this.status = PaymentStatus.PROCESSING;
    }

    /** 결제 성공 확정. 이미 종료 상태면 무시(idempotent). */
    public void markSuccess(String reason) {
        if (status.isFinalized()) {
            return;
        }
        this.status = PaymentStatus.SUCCESS;
        this.reason = reason;
    }

    /** 결제 실패 확정. 이미 종료 상태면 무시(idempotent). */
    public void markFailed(String reason) {
        if (status.isFinalized()) {
            return;
        }
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }

    public Long getUserId() { return userId; }
    public Long getOrderId() { return orderId; }
    public Money getAmount() { return amount; }
    public CardType getCardType() { return cardType; }
    public String getCardNo() { return cardNo; }
    public PaymentStatus getStatus() { return status; }
    public Optional<String> getTransactionKey() { return Optional.ofNullable(transactionKey); }
    public Optional<String> getReason() { return Optional.ofNullable(reason); }
}
