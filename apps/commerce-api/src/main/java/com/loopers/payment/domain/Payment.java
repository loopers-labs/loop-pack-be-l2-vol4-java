package com.loopers.payment.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "payment",
    indexes = {
        @Index(name = "idx_payment_order", columnList = "order_id"),
        @Index(name = "idx_payment_pg_transaction", columnList = "pg_transaction_key")
    }
)
public class Payment extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private long amount;

    @Embedded
    private PaymentCard card;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason")
    private PaymentFailureReason failureReason;

    @Column(name = "pg_transaction_key")
    private String pgTransactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "pg_status")
    private PgPaymentStatus pgStatus;

    @Column(name = "pg_reason")
    private String pgReason;

    @Column(name = "requested_at", nullable = false)
    private ZonedDateTime requestedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    private Payment(
        Long userId,
        Long orderId,
        long amount,
        CardType cardType,
        String cardNo,
        PaymentStatus status,
        PaymentFailureReason failureReason,
        String pgTransactionKey,
        PgPaymentStatus pgStatus,
        String pgReason,
        ZonedDateTime requestedAt,
        ZonedDateTime completedAt
    ) {
        this.userId = requireUserId(userId);
        this.orderId = requireOrderId(orderId);
        this.amount = requireAmount(amount);
        this.card = PaymentCard.of(cardType, cardNo);
        this.status = requireStatus(status);
        this.failureReason = failureReason;
        this.pgTransactionKey = pgTransactionKey;
        this.pgStatus = pgStatus;
        this.pgReason = pgReason;
        this.requestedAt = requireRequestedAt(requestedAt);
        this.completedAt = completedAt;
    }

    public static Payment pending(
        Long userId,
        Long orderId,
        long amount,
        CardType cardType,
        String cardNo,
        String pgTransactionKey,
        ZonedDateTime requestedAt
    ) {
        return new Payment(
            userId,
            orderId,
            amount,
            cardType,
            cardNo,
            PaymentStatus.PENDING,
            null,
            requirePgTransactionKey(pgTransactionKey),
            PgPaymentStatus.PENDING,
            null,
            requestedAt,
            null
        );
    }

    public static Payment unknown(
        Long userId,
        Long orderId,
        long amount,
        CardType cardType,
        String cardNo,
        ZonedDateTime requestedAt
    ) {
        return unknown(userId, orderId, amount, cardType, cardNo, PaymentFailureReason.PG_TIMEOUT, requestedAt);
    }

    public static Payment unknown(
        Long userId,
        Long orderId,
        long amount,
        CardType cardType,
        String cardNo,
        PaymentFailureReason failureReason,
        ZonedDateTime requestedAt
    ) {
        return new Payment(
            userId,
            orderId,
            amount,
            cardType,
            cardNo,
            PaymentStatus.UNKNOWN,
            requireUnknownFailureReason(failureReason),
            null,
            null,
            null,
            requestedAt,
            null
        );
    }

    public static Payment requestFailed(
        Long userId,
        Long orderId,
        long amount,
        CardType cardType,
        String cardNo,
        ZonedDateTime requestedAt
    ) {
        return requestFailed(userId, orderId, amount, cardType, cardNo, PaymentFailureReason.PG_REQUEST_FAILED, requestedAt);
    }

    public static Payment requestFailed(
        Long userId,
        Long orderId,
        long amount,
        CardType cardType,
        String cardNo,
        PaymentFailureReason failureReason,
        ZonedDateTime requestedAt
    ) {
        return new Payment(
            userId,
            orderId,
            amount,
            cardType,
            cardNo,
            PaymentStatus.REQUEST_FAILED,
            requireRequestFailureReason(failureReason),
            null,
            null,
            null,
            requestedAt,
            requestedAt
        );
    }

    public void markSucceeded(String pgTransactionKey, String pgReason, ZonedDateTime completedAt) {
        if (status == PaymentStatus.SUCCEEDED) {
            return;
        }
        validateNotFinalized();

        this.status = PaymentStatus.SUCCEEDED;
        this.failureReason = null;
        this.pgTransactionKey = requirePgTransactionKey(pgTransactionKey);
        this.pgStatus = PgPaymentStatus.SUCCESS;
        this.pgReason = pgReason;
        this.completedAt = requireCompletedAt(completedAt);
    }

    public void markFailed(
        String pgTransactionKey,
        PaymentFailureReason failureReason,
        String pgReason,
        ZonedDateTime completedAt
    ) {
        if (status == PaymentStatus.FAILED) {
            return;
        }
        validateNotFinalized();

        this.status = PaymentStatus.FAILED;
        this.failureReason = requireTransactionFailureReason(failureReason);
        this.pgTransactionKey = requirePgTransactionKey(pgTransactionKey);
        this.pgStatus = PgPaymentStatus.FAILED;
        this.pgReason = pgReason;
        this.completedAt = requireCompletedAt(completedAt);
    }

    @Override
    protected void guard() {
        requireUserId(userId);
        requireOrderId(orderId);
        requireAmount(amount);
        requireCard(card);
        requireStatus(status);
        requireRequestedAt(requestedAt);
    }

    public CardType getCardType() {
        return card.getType();
    }

    public String getMaskedCardNo() {
        return card.getMaskedNo();
    }

    public boolean isInProgress() {
        return status == PaymentStatus.PENDING || status == PaymentStatus.UNKNOWN;
    }

    private void validateNotFinalized() {
        if (status == PaymentStatus.REQUEST_FAILED) {
            throw new CoreException(ErrorType.CONFLICT, "이미 요청 실패로 종료된 결제입니다.");
        }
        if (status == PaymentStatus.SUCCEEDED || status == PaymentStatus.FAILED) {
            throw new CoreException(ErrorType.CONFLICT, "이미 종료된 결제입니다.");
        }
    }

    private static Long requireUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 사용자 ID는 비어있을 수 없습니다.");
        }
        return userId;
    }

    private static Long requireOrderId(Long orderId) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 주문 ID는 비어있을 수 없습니다.");
        }
        return orderId;
    }

    private static long requireAmount(long amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        return amount;
    }

    private static PaymentCard requireCard(PaymentCard card) {
        if (card == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 카드 정보는 비어있을 수 없습니다.");
        }
        return card;
    }

    private static PaymentStatus requireStatus(PaymentStatus status) {
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 상태는 비어있을 수 없습니다.");
        }
        return status;
    }

    private static PaymentFailureReason requireFailureReason(PaymentFailureReason failureReason) {
        if (failureReason == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 실패 사유는 비어있을 수 없습니다.");
        }
        return failureReason;
    }

    private static PaymentFailureReason requireRequestFailureReason(PaymentFailureReason failureReason) {
        PaymentFailureReason required = requireFailureReason(failureReason);
        if (!required.isRequestFailure()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 요청 실패에는 PG 요청 실패 사유만 사용할 수 있습니다.");
        }
        return required;
    }

    private static PaymentFailureReason requireUnknownFailureReason(PaymentFailureReason failureReason) {
        PaymentFailureReason required = requireFailureReason(failureReason);
        if (!required.isUnknownFailure()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "확인이 필요한 결제에는 확인 필요 사유만 사용할 수 있습니다.");
        }
        return required;
    }

    private static PaymentFailureReason requireTransactionFailureReason(PaymentFailureReason failureReason) {
        PaymentFailureReason required = requireFailureReason(failureReason);
        if (!required.isTransactionFailure()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "실패한 결제에는 PG 처리 실패 사유만 사용할 수 있습니다.");
        }
        return required;
    }

    private static String requirePgTransactionKey(String pgTransactionKey) {
        if (pgTransactionKey == null || pgTransactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 거래 키는 비어있을 수 없습니다.");
        }
        return pgTransactionKey;
    }

    private static ZonedDateTime requireRequestedAt(ZonedDateTime requestedAt) {
        if (requestedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 요청 일시는 비어있을 수 없습니다.");
        }
        return requestedAt;
    }

    private static ZonedDateTime requireCompletedAt(ZonedDateTime completedAt) {
        if (completedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 완료 일시는 비어있을 수 없습니다.");
        }
        return completedAt;
    }
}
