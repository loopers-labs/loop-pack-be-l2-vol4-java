package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

/**
 * Payment Aggregate 루트 — 순수 도메인 객체. 한 주문에 대한 외부 결제 시도 한 건을 표현한다.
 * 상태 머신(PENDING → SUCCESS/FAILED)만 보유하고 영속 기술(JPA)에는 의존하지 않는다.
 * JPA 매핑은 infrastructure.payment.PaymentEntity, 변환은 PaymentEntityMapper가 담당한다.
 */
public class PaymentModel {

    private final Long id;          // 영속 전에는 null, 저장 후 매퍼가 채운 값으로 복원
    private final Long orderId;
    private final Long userId;
    private final CardType cardType;
    private final String cardNo;    // 마스킹된 카드번호만 저장 (원본은 PG 호출에만 사용, 영속하지 않음)
    private final Long amount;
    private String transactionKey;  // PG가 발급한 거래 키 (요청 성공 후 채워짐)
    private PaymentStatus status;
    private String reason;
    private final ZonedDateTime createdAt;  // 영속 전에는 null, 복원 시 채워짐 (reconcile 나이 측정용)

    public PaymentModel(Long orderId, Long userId, CardType cardType, String rawCardNo, Long amount) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "orderId는 null일 수 없습니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 null일 수 없습니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 null일 수 없습니다.");
        }
        if (amount == null || amount <= 0L) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 양의 정수여야 합니다.");
        }
        this.id = null;
        this.orderId = orderId;
        this.userId = userId;
        this.cardType = cardType;
        this.cardNo = maskCardNo(rawCardNo);
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.createdAt = null;
    }

    private PaymentModel(Long id, Long orderId, Long userId, CardType cardType, String cardNo, Long amount,
                         String transactionKey, PaymentStatus status, String reason, ZonedDateTime createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.userId = userId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.transactionKey = transactionKey;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    /** 영속 데이터로부터 도메인 객체를 복원한다 (infrastructure 매퍼 전용). cardNo는 이미 마스킹된 값. */
    public static PaymentModel reconstitute(Long id, Long orderId, Long userId, CardType cardType, String cardNo,
                                            Long amount, String transactionKey, PaymentStatus status, String reason,
                                            ZonedDateTime createdAt) {
        return new PaymentModel(id, orderId, userId, cardType, cardNo, amount, transactionKey, status, reason, createdAt);
    }

    /** PG 요청 성공 후 발급받은 거래 키를 부여한다. */
    public void assignTransactionKey(String transactionKey) {
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "transactionKey는 비어 있을 수 없습니다.");
        }
        this.transactionKey = transactionKey;
    }

    /** PENDING → SUCCESS. 다른 상태에서 호출 시 CONFLICT (콜백/Reconcile 중복 확정 방지). */
    public void markSuccess() {
        requirePending();
        this.status = PaymentStatus.SUCCESS;
        this.reason = null;
    }

    /** PENDING → FAILED. 다른 상태에서 호출 시 CONFLICT. */
    public void markFailed(String reason) {
        requirePending();
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }

    private void requirePending() {
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "PENDING 상태에서만 결제 결과를 반영할 수 있습니다. (현재: " + this.status + ")");
        }
    }

    /** "1234-5678-9814-1451" → "1234-****-****-1451". 형식이 다르면 앞 4자만 남기고 마스킹. */
    private static String maskCardNo(String rawCardNo) {
        if (rawCardNo == null || rawCardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 비어 있을 수 없습니다.");
        }
        String[] groups = rawCardNo.split("-");
        if (groups.length == 4) {
            return groups[0] + "-****-****-" + groups[3];
        }
        int keep = Math.min(4, rawCardNo.length());
        return rawCardNo.substring(0, keep) + "****";
    }

    public Long getId() {
        return id;
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

    /** 영속 생성 시각. 신규(미저장) 객체는 null. reconcile이 PENDING 체류 시간을 잴 때 쓴다. */
    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }
}
