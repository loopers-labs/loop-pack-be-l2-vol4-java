package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 결제 시도(Attempt). 한 주문에 여러 Payment 가 존재할 수 있다 (재결제 대비, 1:N).
 *
 *  - 카드번호는 마지막 4자리(cardLastFour) 만 보관한다. 실무에서는 PG 토큰화 필수이며, 평문 저장은 PCI-DSS 위반.
 *    PG 호출 시점에 원본 카드번호를 메모리에서만 사용한 뒤 즉시 폐기.
 *  - transactionKey 는 PG 호출 성공(IN_PROGRESS) 시점에 발급된다. REQUESTED / UNKNOWN(호출 실패) 시에는 null.
 *  - completedAt 은 SUCCESS/FAILED 로 확정된 시각.
 *
 *  상태 전이는 markInProgress / markSuccess / markFailed / markUnknown 으로만 가능하고
 *  모두 멱등하게 동작한다 (콜백 + 폴링이 동시에 같은 전이를 시도하는 케이스 대비).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "payment",
    indexes = {
        // 한 주문의 결제 시도 조회 (멱등 체크 / 콜백 매칭 대비)
        @Index(name = "idx_payment_order_id", columnList = "order_id"),
        // 폴링 대상 조회: status IN (IN_PROGRESS, UNKNOWN) + 시간 조건
        @Index(name = "idx_payment_status_created", columnList = "status, created_at"),
        // 콜백 매칭 — transactionKey 로 단건 조회 (unique 까지 보장)
        @Index(name = "idx_payment_transaction_key", columnList = "transaction_key", unique = true)
    }
)
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private PgProvider provider;

    @Column(name = "amount", nullable = false)
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 16)
    private CardType cardType;

    /** 카드번호 마지막 4자리 (마스킹된 표시용). 원본 카드번호는 저장하지 않는다. */
    @Column(name = "card_last_four", nullable = false, length = 4)
    private String cardLastFour;

    /** PG 가 발급한 거래 키. 호출 성공 후에만 채워진다. */
    @Column(name = "transaction_key", unique = true)
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "reason")
    private String reason;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Payment(Long orderId, Long userId, PgProvider provider, Money amount,
                    CardType cardType, String cardLastFour) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제에는 주문 정보가 필요합니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제에는 유저 정보가 필요합니다.");
        }
        if (provider == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 대행사(PG)는 비어있을 수 없습니다.");
        }
        if (amount == null || amount.isZero()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 비어있을 수 없습니다.");
        }
        if (cardLastFour == null || cardLastFour.length() != 4) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 마지막 4자리는 4자여야 합니다.");
        }

        this.orderId = orderId;
        this.userId = userId;
        this.provider = provider;
        this.amount = amount;
        this.cardType = cardType;
        this.cardLastFour = cardLastFour;
        this.status = PaymentStatus.REQUESTED;
    }

    /**
     * 결제 시도 생성. status 는 REQUESTED 로 시작하고, PG 호출 결과에 따라 전이된다.
     */
    public static Payment request(Long orderId, Long userId, PgProvider provider, Money amount,
                                  CardType cardType, String cardLastFour) {
        return Payment.builder()
            .orderId(orderId)
            .userId(userId)
            .provider(provider)
            .amount(amount)
            .cardType(cardType)
            .cardLastFour(cardLastFour)
            .build();
    }

    /**
     * PG 가 transactionKey 를 발급해 처리 중 상태로 전이. REQUESTED 에서만 가능.
     */
    public void markInProgress(String transactionKey) {
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "transactionKey 가 비어있을 수 없습니다.");
        }
        if (this.status == PaymentStatus.IN_PROGRESS && transactionKey.equals(this.transactionKey)) {
            return; // 멱등: 동일 transactionKey 재호출 무시
        }
        if (this.status != PaymentStatus.REQUESTED) {
            throw new CoreException(ErrorType.CONFLICT,
                "IN_PROGRESS 로 전이할 수 없는 상태입니다. [현재 = " + this.status + "]");
        }
        this.status = PaymentStatus.IN_PROGRESS;
        this.transactionKey = transactionKey;
    }

    /**
     * 결제 성공 확정. 멱등하게 동작 (이미 SUCCESS 면 무시).
     * 단, FAILED 에서 SUCCESS 로의 전이는 위험 (이미 재고 복구됐을 수 있음) → 예외로 알리고 운영 개입.
     */
    public void markSuccess() {
        if (this.status == PaymentStatus.SUCCESS) {
            return; // 멱등
        }
        if (this.status == PaymentStatus.FAILED) {
            throw new CoreException(ErrorType.CONFLICT,
                "FAILED 상태에서 SUCCESS 전이는 불가합니다. 운영 확인 필요. [paymentId = " + getId() + "]");
        }
        if (this.status != PaymentStatus.IN_PROGRESS && this.status != PaymentStatus.UNKNOWN) {
            throw new CoreException(ErrorType.CONFLICT,
                "SUCCESS 로 전이할 수 없는 상태입니다. [현재 = " + this.status + "]");
        }
        this.status = PaymentStatus.SUCCESS;
        this.reason = "정상 승인되었습니다.";
        this.completedAt = ZonedDateTime.now();
    }

    /**
     * 결제 실패 확정. 멱등하게 동작.
     */
    public void markFailed(String reason) {
        if (this.status == PaymentStatus.FAILED) {
            return; // 멱등
        }
        if (this.status == PaymentStatus.SUCCESS) {
            throw new CoreException(ErrorType.CONFLICT,
                "SUCCESS 상태에서 FAILED 전이는 불가합니다. [paymentId = " + getId() + "]");
        }
        this.status = PaymentStatus.FAILED;
        this.reason = (reason == null || reason.isBlank()) ? "결제 실패" : reason;
        this.completedAt = ZonedDateTime.now();
    }

    /**
     * PG 호출 자체가 실패/timeout 으로 결과를 모르는 상태로 전이. 폴링이 확정할 때까지 보존.
     * REQUESTED 에서만 가능 (이미 진행 중인 결제는 UNKNOWN 으로 되돌리지 않는다).
     */
    public void markUnknown(String reason) {
        if (this.status == PaymentStatus.UNKNOWN) {
            return; // 멱등
        }
        if (this.status != PaymentStatus.REQUESTED) {
            throw new CoreException(ErrorType.CONFLICT,
                "UNKNOWN 으로 전이할 수 없는 상태입니다. [현재 = " + this.status + "]");
        }
        this.status = PaymentStatus.UNKNOWN;
        this.reason = (reason == null || reason.isBlank()) ? "PG 호출 결과 미확정" : reason;
    }

    /** 멱등 체크용 — 해당 결제가 이미 진행/완료 상태인지. */
    public boolean isInProgressOrFinal() {
        return this.status != PaymentStatus.REQUESTED;
    }
}
