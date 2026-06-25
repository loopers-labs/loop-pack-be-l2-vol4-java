package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "payments")
@SQLRestriction("deleted_at IS NULL")
public class PaymentModel extends BaseEntity {

    private Long orderId;

    @Enumerated(EnumType.STRING)
    private CardType cardType;

    private String cardNo;

    private BigDecimal amount;

    private String transactionKey;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    protected PaymentModel() {
    }

    public PaymentModel(Long orderId, CardType cardType, String cardNo, BigDecimal amount) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 정보가 없습니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 타입은 필수입니다.");
        }
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        this.orderId = orderId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public void update(String transactionKey, TransactionStatus status) {
        this.transactionKey = transactionKey;
        switch (status) {
            case SUCCESS -> this.status = PaymentStatus.PAID;
            case FAILED -> this.status = PaymentStatus.FAILED;
            case PENDING -> this.status = PaymentStatus.PENDING;
        }
    }

    public void markAsFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public void markAsUnknown() {
        this.status = PaymentStatus.UNKNOWN;
    }
}
