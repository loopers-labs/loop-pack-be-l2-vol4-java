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
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.util.regex.Pattern;

@Getter
@Entity
@Table(
        name = "payment",
        indexes = {
                @Index(name = "idx_payment_transaction_key", columnList = "transactionKey"),
                @Index(name = "idx_payment_order_status", columnList = "orderId, status"),
                @Index(name = "idx_payment_status_created", columnList = "status, createdAt"),
        }
)
@SQLRestriction("deleted_at IS NULL")
public class PaymentModel extends BaseEntity {

    static final Pattern CARD_NO_PATTERN = Pattern.compile("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$");

    private Long orderId;

    private String orderNumber;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private CardType cardType;

    private String cardNo;

    private Long amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String transactionKey;

    private String failureReason;

    protected PaymentModel() {
    }

    private PaymentModel(Long orderId, String orderNumber, Long userId, CardType cardType, String cardNo, Long amount) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 정보가 없습니다.");
        }
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 번호가 없습니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 정보가 없습니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류가 없습니다.");
        }
        if (cardNo == null || !CARD_NO_PATTERN.matcher(cardNo).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.");
        }
        if (amount == null || amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0 보다 큰 정수여야 합니다.");
        }
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.cardType = cardType;
        this.cardNo = mask(cardNo);
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    /**
     * 결제 요청 접수 시점의 Payment(PENDING)를 생성한다.
     * 카드 번호는 마스킹하여 저장한다(원문 카드 번호는 PG 호출용으로만 사용하고 보관하지 않는다).
     */
    public static PaymentModel pending(Long orderId, String orderNumber, Long userId,
                                       CardType cardType, String cardNo, Long amount) {
        return new PaymentModel(orderId, orderNumber, userId, cardType, cardNo, amount);
    }

    /**
     * 카드 번호를 마스킹한다. {@code 1234-5678-9814-1451 -> 1234-****-****-1451}.
     * 무결성 가드(콜백 cardNo 대조)에서도 동일 규칙으로 비교하기 위해 정적 메서드로 노출한다.
     */
    public static String mask(String cardNo) {
        if (cardNo == null || !CARD_NO_PATTERN.matcher(cardNo).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.");
        }
        String[] groups = cardNo.split("-");
        return groups[0] + "-****-****-" + groups[3];
    }

    public void markPaid(String transactionKey) {
        if (this.status == PaymentStatus.PAID) {
            return; // 멱등: 이미 성공 확정
        }
        requirePending("결제 완료 확정");
        this.status = PaymentStatus.PAID;
        if (transactionKey != null) {
            this.transactionKey = transactionKey;
        }
    }

    public void markFailed(String reason) {
        if (this.status == PaymentStatus.FAILED) {
            return; // 멱등: 이미 실패 확정
        }
        requirePending("결제 실패 확정");
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public void markUnknown() {
        if (this.status == PaymentStatus.UNKNOWN) {
            return; // 멱등
        }
        requirePending("미확정 격리");
        this.status = PaymentStatus.UNKNOWN;
    }

    /**
     * 수동 복구: UNKNOWN 격리 건을 PENDING 으로 되돌려 재확정을 시도할 수 있게 한다.
     * terminal(PAID/FAILED)은 불변이므로 되돌릴 수 없다.
     */
    public void restorePending() {
        if (this.status == PaymentStatus.PENDING) {
            return; // 멱등
        }
        if (this.status != PaymentStatus.UNKNOWN) {
            throw new CoreException(ErrorType.CONFLICT,
                    "PENDING 복구는 UNKNOWN 상태에서만 가능합니다. (현재 상태=" + this.status + ")");
        }
        this.status = PaymentStatus.PENDING;
    }

    /**
     * PG 접수 응답으로 받은 transactionKey 를 반영한다. 상태는 PENDING 으로 유지된다.
     */
    public void attachTransactionKey(String transactionKey) {
        requirePending("거래키 등록");
        this.transactionKey = transactionKey;
    }

    private void requirePending(String action) {
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT,
                    action + "는 PENDING 상태에서만 가능합니다. (현재 상태=" + this.status + ")");
        }
    }
}
