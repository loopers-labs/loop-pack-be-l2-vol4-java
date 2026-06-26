package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

/**
 * 결제 한 "시도" 를 나타내는 애그리거트 루트.
 *
 * <p>결정2=B: 주문에 대해 시도마다 Payment 1건이 생성된다(N:1). 주문은 최신 시도를 조회로 도출한다.
 * 주문은 다른 애그리거트이므로 {@code orderId} 로만 참조한다.</p>
 *
 * <p>{@code userId} 를 보유하지 않는다: 결제 주체는 {@code orderId → Order.userId} 로 도출되는 파생값이고
 * 주문 소유자는 불변이라 스냅샷 의미가 없다(반면 {@code amount} 는 결제 시점 금액 박제라 보유). PG 조회에 필요한
 * userId 는 정산 시 응용이 Order 에서 조달해 전달한다. cf. 결제 시점에 변하지 않는 파생값은 도출, 변할 수 있는 값만 스냅샷.</p>
 *
 * <p>{@code transactionKey} 는 PG 가 발급한다 → 요청이 접수되기 전(미접수/요청실패)에는 {@code null} 이다.
 * 결과 확정은 콜백/정산에서 {@link #markSuccess()} / {@link #markFailed(String)} 로 이뤄지며,
 * 이미 종결된 결제에 대한 재호출은 멱등하게 무시된다(콜백 중복·정산 동시성 흡수).</p>
 *
 * <p>보안(PCI): 풀 카드번호(PAN)는 PG 호출 순간에만 쓰이는 transient 값이며 엔티티에 영속하지 않는다.
 * 표시·감사용으로 마스킹된 번호({@code **** **** **** 1451})만 보관한다.</p>
 */
@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    private static final Pattern CARD_NO = Pattern.compile("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$");

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "transaction_key", unique = true)
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    /** 표시·감사용 마스킹 번호(예: {@code **** **** **** 1451}). 풀 PAN 은 저장하지 않는다. */
    @Column(name = "masked_card_no", nullable = false, length = 19)
    private String maskedCardNo;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "reason")
    private String reason;

    private Payment(Long orderId, CardType cardType, String cardNo, Money amount) {
        validateOrderId(orderId);
        validateCardType(cardType);
        validateCardNo(cardNo);
        validateAmount(amount);
        this.orderId = orderId;
        this.cardType = cardType;
        this.maskedCardNo = mask(cardNo);
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    /**
     * 결제 시도를 생성한다. PG 요청 전 상태이므로 {@code transactionKey} 는 아직 비어 있고 상태는 PENDING 이다.
     *
     * <p>{@code cardNo}(풀 PAN)는 형식 검증에만 쓰이고 엔티티에는 마스킹된 형태로만 보관된다.
     * PG 호출에 필요한 풀 PAN 은 호출자가 별도로 보유해 전달한다.</p>
     */
    public static Payment create(Long orderId, CardType cardType, String cardNo, Money amount) {
        return new Payment(orderId, cardType, cardNo, amount);
    }

    /** {@code 1234-5678-9814-1451} → {@code **** **** **** 1451} (마지막 4자리만 노출). */
    private static String mask(String cardNo) {
        String last4 = cardNo.substring(cardNo.length() - 4);
        return "**** **** **** " + last4;
    }

    /**
     * PG 가 요청을 접수하고 발급한 트랜잭션 키를 보관한다(접수 확인). 결과 확정은 아니다.
     */
    public void assignTransactionKey(String transactionKey) {
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "트랜잭션 키는 비어있을 수 없습니다.");
        }
        this.transactionKey = transactionKey;
    }

    /**
     * 성공으로 확정한다. 이미 종결된 결제면 멱등하게 무시하고 {@code false} 를 반환한다.
     *
     * @return 이번 호출로 PENDING → SUCCESS 전이가 실제로 일어났으면 {@code true}
     */
    public boolean markSuccess() {
        if (status.isTerminal()) {
            return false;
        }
        this.status = PaymentStatus.SUCCESS;
        this.reason = null;
        return true;
    }

    /**
     * 실패로 확정한다. 이미 종결된 결제면 멱등하게 무시하고 {@code false} 를 반환한다.
     *
     * @return 이번 호출로 PENDING → FAILED 전이가 실제로 일어났으면 {@code true}
     */
    public boolean markFailed(String reason) {
        if (status.isTerminal()) {
            return false;
        }
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
        return true;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean isSuccess() {
        return status == PaymentStatus.SUCCESS;
    }

    private static void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 비어있을 수 없습니다.");
        }
    }

    private static void validateCardType(CardType cardType) {
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 비어있을 수 없습니다.");
        }
    }

    private static void validateCardNo(String cardNo) {
        if (cardNo == null || !CARD_NO.matcher(cardNo).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.");
        }
    }

    private static void validateAmount(Money amount) {
        if (amount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 비어있을 수 없습니다.");
        }
    }
}
