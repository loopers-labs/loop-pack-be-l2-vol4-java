package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.regex.Pattern;

@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_order", columnList = "order_id"),
        @Index(name = "uk_payment_transaction_key", columnList = "transaction_key", unique = true)
    }
)
public class PaymentModel extends BaseEntity {

    private static final Pattern CARD_NO_PATTERN = Pattern.compile("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$");

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "transaction_key", unique = true)
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "reason")
    private String reason;

    protected PaymentModel() {}

    public PaymentModel(Long userId, Long orderId, CardType cardType, String cardNo, Long amount) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저는 필수입니다.");
        }
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문은 필수입니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 필수입니다.");
        }
        if (cardNo == null || !CARD_NO_PATTERN.matcher(cardNo).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.");
        }
        if (amount == null || amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        this.userId = userId;
        this.orderId = orderId;
        this.cardType = cardType;
        this.cardNo = mask(cardNo);
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    private static final String MASKED_CARD_PREFIX = "****-****-****-";
    private static final int VISIBLE_CARD_DIGITS = 4;

    private static String mask(String cardNo) {
        return MASKED_CARD_PREFIX + cardNo.substring(cardNo.length() - VISIBLE_CARD_DIGITS);
    }

    /**
     * PG가 발급한 트랜잭션 키를 연결한다. 최초 1회만 설정되며, 같은 키 재연결은 멱등하게 무시한다.
     */
    public void linkTransactionKey(String transactionKey) {
        if (this.transactionKey == null) {
            this.transactionKey = transactionKey;
            return;
        }
        if (this.transactionKey.equals(transactionKey)) {
            return;
        }
        throw new CoreException(ErrorType.CONFLICT, "이미 다른 트랜잭션 키가 연결된 결제입니다.");
    }

    /**
     * 결제를 성공으로 전이한다. PENDING에서만 전이하며, 이미 SUCCESS면 멱등하게 무시한다.
     * 이미 FAILED인 경우(역전)는 CONFLICT.
     */
    public void markSuccess(String reason) {
        if (this.status == PaymentStatus.SUCCESS) {
            return;
        }
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "대기 상태의 결제만 성공 처리할 수 있습니다.");
        }
        this.status = PaymentStatus.SUCCESS;
        this.reason = reason;
    }

    /**
     * 결제를 실패로 전이한다. PENDING에서만 전이하며, 이미 FAILED면 멱등하게 무시한다.
     * 이미 SUCCESS인 경우(역전)는 CONFLICT.
     */
    public void markFailed(String reason) {
        if (this.status == PaymentStatus.FAILED) {
            return;
        }
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "대기 상태의 결제만 실패 처리할 수 있습니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getTransactionKey() {
        return transactionKey;
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

    public PaymentStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
