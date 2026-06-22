package com.loopers.infrastructure.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * payments 테이블 JPA 매핑 전용 엔티티. 순수 도메인(PaymentModel)과 분리되어 영속 관심사만 담는다.
 * 도메인 ↔ 엔티티 변환은 PaymentEntityMapper가 담당.
 */
@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(name = "idx_payment_order", columnList = "order_id"),
                @Index(name = "idx_payment_transaction_key", columnList = "transaction_key", unique = true),
                @Index(name = "idx_payment_status", columnList = "status")
        }
)
public class PaymentEntity extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    @Column(name = "card_no", nullable = false, length = 30)
    private String cardNo;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "transaction_key", length = 50)
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "reason", length = 500)
    private String reason;

    protected PaymentEntity() {}

    public PaymentEntity(Long orderId, Long userId, CardType cardType, String cardNo, Long amount,
                         String transactionKey, PaymentStatus status, String reason) {
        this.orderId = orderId;
        this.userId = userId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.transactionKey = transactionKey;
        this.status = status;
        this.reason = reason;
    }

    /**
     * 가변 상태(거래 키/상태/사유)만 갱신한다. managed 엔티티에 적용 → dirty checking으로 UPDATE.
     * (orderId/userId/cardType/cardNo/amount는 불변)
     */
    public void applyState(String transactionKey, PaymentStatus status, String reason) {
        this.transactionKey = transactionKey;
        this.status = status;
        this.reason = reason;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public CardType getCardType() {
        return cardType;
    }

    public String getCardNo() {
        return cardNo;
    }

    public Long getAmount() {
        return amount;
    }

    public String getTransactionKey() {
        return transactionKey;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
