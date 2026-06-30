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
import jakarta.persistence.Version;

/**
 * 결제 — PG 거래의 로컬 투영(local projection). orderId 기준 멱등 가드 + 상태 전이로
 * 외부(PG) 결과를 내부 상태에 안전하게 반영한다. 카드번호(PAN)는 저장하지 않는다(PCI).
 * 동시 도착(콜백+폴링) 시 1회만 전이하도록 @Version 낙관적 락을 둔다.
 */
@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Column(name = "reason")
    private String reason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Payment() {}

    private Payment(Long userId, Long orderId, Money amount, CardType cardType) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
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
        this.userId = userId;
        this.orderId = orderId;
        this.amount = amount;
        this.cardType = cardType;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment pending(Long userId, Long orderId, Money amount, CardType cardType) {
        return new Payment(userId, orderId, amount, cardType);
    }

    /** PG 가 거래를 접수해 발급한 키를 기록한다 (PENDING 동안). */
    public void assignTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }

    /** PENDING → SUCCESS. 터미널 상태면 재전이를 막는다(불변식). 중복 도착 무시는 호출부(조건부 갱신)가 담당. */
    public void markSuccess() {
        requirePending();
        this.status = PaymentStatus.SUCCESS;
    }

    /** PENDING → FAILED(+사유). 터미널 상태면 재전이를 막는다(불변식). */
    public void markFailed(String reason) {
        requirePending();
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }

    private void requirePending() {
        if (status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 처리된 결제입니다. (status=" + status + ")");
        }
    }

    public Long getUserId() {
        return userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getAmount() {
        return amount.getAmount();
    }

    public CardType getCardType() {
        return cardType;
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
