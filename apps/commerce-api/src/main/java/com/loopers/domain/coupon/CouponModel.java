package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿 (어드민이 관리하는 발급 원본).
 *
 * <p>실제 사용자가 보유·사용하는 것은 {@link UserCouponModel}(발급분)이며,
 * 이 템플릿은 할인 정책(타입/값/최소주문금액/만료시각)만 정의한다.
 * 발급 시 혜택이 발급분으로 스냅샷되므로, 템플릿 수정은 이후 발급분에만 영향을 준다 —
 * 검증/할인 계산은 발급분({@code UserCouponModel.validateApplicable/calculateDiscount})이 수행한다.
 *
 * <p><strong>선착순 수량 (Round 7)</strong>: {@code totalQuantity}(null=무제한) 한도 내에서만 발급된다.
 * 차감은 조회-검사-갱신(TOCTOU)이 아니라 조건부 원자 UPDATE
 * ({@code CouponRepository#tryIncreaseIssuedCount})로 수행해 동시 발급에도 초과가 불가능하다.
 */
@Entity
@Table(name = "coupons")
public class CouponModel extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    /** 할인 값. FIXED=할인금액(원), RATE=할인율(%). */
    @Column(nullable = false)
    private long value;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "min_order_amount", nullable = false))
    private Money minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    /** 선착순 총 발급 수량. null = 무제한. */
    @Column(name = "total_quantity")
    private Integer totalQuantity;

    /** 현재까지 발급된 수량 — 조건부 원자 UPDATE 로만 증가한다. */
    @Column(name = "issued_count", nullable = false)
    private long issuedCount;

    protected CouponModel() {}

    public CouponModel(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        this(name, type, value, minOrderAmount, expiredAt, null);   // 수량 미지정 = 무제한
    }

    public CouponModel(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt, Integer totalQuantity) {
        validateFields(name, type, value, expiredAt);
        validateQuantity(totalQuantity, 0L);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = Money.of(minOrderAmount == null ? 0L : minOrderAmount);   // 미지정 시 최소금액 조건 없음
        this.expiredAt = expiredAt;
        this.totalQuantity = totalQuantity;
        this.issuedCount = 0L;
    }

    public void update(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt, Integer totalQuantity) {
        validateFields(name, type, value, expiredAt);
        validateQuantity(totalQuantity, this.issuedCount);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = Money.of(minOrderAmount == null ? 0L : minOrderAmount);
        this.expiredAt = expiredAt;
        this.totalQuantity = totalQuantity;
    }

    private static void validateQuantity(Integer totalQuantity, long issuedCount) {
        if (totalQuantity != null && totalQuantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 발급 수량은 1 이상이어야 합니다. (무제한은 미지정)");
        }
        if (totalQuantity != null && totalQuantity < issuedCount) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "총 발급 수량은 이미 발급된 수량(" + issuedCount + ")보다 작을 수 없습니다.");
        }
    }

    private void validateFields(String name, CouponType type, long value, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 시각은 필수입니다.");
        }
        type.validateValue(value);
    }

    public boolean isExpired(ZonedDateTime now) {
        return expiredAt.isBefore(now);
    }

    public String getName() {
        return name;
    }

    public CouponType getType() {
        return type;
    }

    public long getValue() {
        return value;
    }

    public Long getMinOrderAmount() {
        return minOrderAmount.getAmount();
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public long getIssuedCount() {
        return issuedCount;
    }
}
