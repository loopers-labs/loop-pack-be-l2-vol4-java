package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentModel extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "reason")
    private String reason;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Column(name = "pg_request_attempted", nullable = false)
    private boolean pgRequestAttempted;

    @Column(name = "recovery_attempts", nullable = false)
    private int recoveryAttempts;

    private PaymentModel(Long orderId, Long userId, Long amount, CardType cardType, String cardNo) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.status = PaymentStatus.PENDING;
        this.transactionKey = null;
        this.pgRequestAttempted = false;
        this.recoveryAttempts = 0;
    }

    public static PaymentModel of(Long orderId, Long userId, Long amount, CardType cardType, String cardNo) {
        return new PaymentModel(orderId, userId, amount, cardType, cardNo);
    }

    /** PG 접수 성공: transactionKey 세팅 + 시도 플래그 on. */
    public void markRequested(String transactionKey) {
        this.transactionKey = transactionKey;
        this.pgRequestAttempted = true;
    }

    /** 타임아웃: 거래가 PG 에 생성됐을 수 있으나 key 미수령. 역조회 복구 대상. */
    public void markAttemptedWithoutKey() {
        this.pgRequestAttempted = true;
    }

    /** 복구 폴링 1회 시도(미해결) 기록. 상한 초과 시 폴링 대상에서 제외된다. */
    public void increaseRecoveryAttempts() {
        this.recoveryAttempts++;
    }

    /** 종결 상태면 무시(멱등). PENDING 일 때만 전이. */
    public void succeed() {
        if (this.status != PaymentStatus.PENDING) {
            return;
        }
        this.status = PaymentStatus.SUCCESS;
        this.reason = null;
    }

    /** 종결 상태면 무시(멱등). PENDING 일 때만 전이. */
    public void fail(String reason) {
        if (this.status != PaymentStatus.PENDING) {
            return;
        }
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }

    public boolean isPending() {
        return this.status == PaymentStatus.PENDING;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PaymentModel that)) {
            return false;
        }

        Long id = getId();
        Long otherId = that.getId();
        if (id == null || id == 0L || otherId == null || otherId == 0L) {
            return false;
        }
        return Objects.equals(id, otherId);
    }

    @Override
    public int hashCode() {
        return PaymentModel.class.hashCode();
    }
}