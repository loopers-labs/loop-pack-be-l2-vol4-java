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
import jakarta.persistence.UniqueConstraint;

import java.util.regex.Pattern;

/**
 * 결제 Aggregate.
 * <p>
 * 멱등성: orderId UNIQUE — 한 주문에 결제는 하나만. 중복 요청은 도메인이 막는다.
 * <p>
 * 상태 전이는 도메인 메서드로만 가능하다 (markRequested / markSucceeded / markFailed / markTimeoutPending).
 * 종료(SUCCEEDED/FAILED) 후엔 전이 불가 — 멘토 리뷰 효율을 위해 명시적으로 예외.
 */
@Entity
@Table(
    name = "payment",
    uniqueConstraints = @UniqueConstraint(name = "uk_payment_order_id", columnNames = "order_id"),
    indexes = {
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_tx_key", columnList = "transaction_key")
    }
)
public class PaymentModel extends BaseEntity {

    private static final Pattern CARD_NO_PATTERN = Pattern.compile("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$");

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    @Column(name = "card_no", nullable = false, length = 19)
    private String cardNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** PG 가 발급한 거래 키. 요청 접수 후 채워진다. */
    @Column(name = "transaction_key", length = 40)
    private String transactionKey;

    /** 실패/타임아웃 사유 (운영 디버깅용). */
    @Column(name = "fail_reason", length = 200)
    private String failReason;

    protected PaymentModel() {}

    public PaymentModel(Long orderId, Long userId, Long amount, CardType cardType, String cardNo) {
        validateOrderId(orderId);
        validateUserId(userId);
        validateAmount(amount);
        validateCardType(cardType);
        validateCardNo(cardNo);

        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.status = PaymentStatus.PENDING;
    }

    public Long getOrderId() { return orderId; }
    public Long getUserId() { return userId; }
    public Long getAmount() { return amount; }
    public CardType getCardType() { return cardType; }
    public String getCardNo() { return cardNo; }
    public PaymentStatus getStatus() { return status; }
    public String getTransactionKey() { return transactionKey; }
    public String getFailReason() { return failReason; }

    /**
     * PG 가 요청을 접수해 transactionKey 를 발급한 상태로 전이. PENDING 에서만 가능.
     */
    public void markRequested(String transactionKey) {
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 거래 키는 비어있을 수 없습니다.");
        }
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT,
                "REQUESTED 로 전이 가능한 상태는 PENDING 뿐입니다. 현재: " + this.status);
        }
        this.transactionKey = transactionKey;
        this.status = PaymentStatus.REQUESTED;
    }

    /**
     * PG 호출이 타임아웃/회로차단 등으로 응답을 받지 못한 상태. 복구 스케줄러가 추후 동기화한다.
     * PENDING 에서만 가능 (이미 REQUESTED 받은 건은 콜백/조회로 복구).
     */
    public void markTimeoutPending(String reason) {
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT,
                "TIMEOUT_PENDING 로 전이 가능한 상태는 PENDING 뿐입니다. 현재: " + this.status);
        }
        this.status = PaymentStatus.TIMEOUT_PENDING;
        this.failReason = truncate(reason);
    }

    /**
     * 결제 성공 확정. 콜백/복구 폴링 어디서 호출되어도 멱등하게 동작 — 이미 SUCCEEDED 면 통과,
     * 이미 FAILED 면 CONFLICT (PG 가 모순된 결과를 보낸 것).
     */
    public void markSucceeded() {
        if (this.status == PaymentStatus.SUCCEEDED) return;
        if (this.status == PaymentStatus.FAILED) {
            throw new CoreException(ErrorType.CONFLICT,
                "이미 FAILED 인 결제는 SUCCEEDED 로 전이할 수 없습니다.");
        }
        this.status = PaymentStatus.SUCCEEDED;
    }

    /**
     * 결제 실패 확정. 멱등하게 동작 — 이미 FAILED 면 통과, 이미 SUCCEEDED 면 CONFLICT.
     */
    public void markFailed(String reason) {
        if (this.status == PaymentStatus.FAILED) return;
        if (this.status == PaymentStatus.SUCCEEDED) {
            throw new CoreException(ErrorType.CONFLICT,
                "이미 SUCCEEDED 인 결제는 FAILED 로 전이할 수 없습니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.failReason = truncate(reason);
    }

    /**
     * 복구 폴링에서 transactionKey 를 PG GET (by orderId) 으로 알아낸 경우 채워준다.
     * TIMEOUT_PENDING 인 결제건에만 의미가 있다.
     */
    public void attachTransactionKey(String transactionKey) {
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 거래 키는 비어있을 수 없습니다.");
        }
        if (this.transactionKey != null) return; // 멱등
        this.transactionKey = transactionKey;
    }

    private static void validateOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID 는 양수여야 합니다.");
        }
    }

    private static void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID 는 양수여야 합니다.");
        }
    }

    private static void validateAmount(Long amount) {
        if (amount == null || amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0 이상이어야 합니다.");
        }
    }

    private static void validateCardType(CardType cardType) {
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 비어있을 수 없습니다.");
        }
    }

    private static void validateCardNo(String cardNo) {
        if (cardNo == null || !CARD_NO_PATTERN.matcher(cardNo).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호 형식이 올바르지 않습니다. (xxxx-xxxx-xxxx-xxxx)");
        }
    }

    private static String truncate(String reason) {
        if (reason == null) return null;
        return reason.length() > 200 ? reason.substring(0, 200) : reason;
    }
}
