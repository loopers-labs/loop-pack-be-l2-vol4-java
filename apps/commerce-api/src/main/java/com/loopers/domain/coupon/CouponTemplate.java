package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 관리자가 등록하는 쿠폰 마스터(템플릿).
 * 발급된 쿠폰(UserCoupon) 들이 이 템플릿을 참조해 할인을 적용한다.
 */
@Entity
@Table(name = "coupon_templates")
public class CouponTemplate extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CouponType type;

    @Column(nullable = false)
    private Long value;

    @Column(name = "min_order_amount", nullable = false)
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    /**
     * 선착순 발급 총량. null = 무제한. 7주차 도입.
     */
    @Column(name = "total_stock")
    private Long totalStock;

    @Column(name = "issued_count", nullable = false)
    private Long issuedCount;

    protected CouponTemplate() {}

    public CouponTemplate(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        this(name, type, value, minOrderAmount, expiredAt, null);
    }

    public CouponTemplate(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt, Long totalStock) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 비어있을 수 없습니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 value 는 1 이상이어야 합니다.");
        }
        if (type == CouponType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰의 value 는 100 이하여야 합니다.");
        }
        if (minOrderAmount == null || minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 일시는 필수입니다.");
        }
        if (totalStock != null && totalStock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 재고는 0 이상이어야 합니다.");
        }
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.totalStock = totalStock;
        this.issuedCount = 0L;
    }

    public String getName() {
        return name;
    }

    public CouponType getType() {
        return type;
    }

    public Long getValue() {
        return value;
    }

    public Long getMinOrderAmount() {
        return minOrderAmount;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    public Long getTotalStock() {
        return totalStock;
    }

    public Long getIssuedCount() {
        return issuedCount;
    }

    /**
     * 선착순 발급을 시도한다.
     * <ul>
     *   <li>totalStock 이 null 이면 무제한 — 항상 성공.</li>
     *   <li>issuedCount < totalStock 이면 카운트 1 증가.</li>
     *   <li>이미 소진(issuedCount >= totalStock)이면 CONFLICT.</li>
     * </ul>
     * 동시성은 호출자 트랜잭션 + 파티션 순차 처리로 보장 — 도메인은 단일 스레드 관점의 규칙만.
     */
    public void issueOne() {
        if (totalStock == null) {
            this.issuedCount += 1;
            return;
        }
        if (this.issuedCount >= this.totalStock) {
            throw new CoreException(ErrorType.CONFLICT, "쿠폰이 모두 소진되었습니다.");
        }
        this.issuedCount += 1;
    }

    public boolean isSoldOut() {
        return totalStock != null && issuedCount >= totalStock;
    }

    public void update(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        // 모든 필드 재검증 — 생성자와 동일 규칙 (DRY 보다 명시성 우선)
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 비어있을 수 없습니다.");
        }
        if (type == null || value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 type/value 가 올바르지 않습니다.");
        }
        if (type == CouponType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰의 value 는 100 이하여야 합니다.");
        }
        if (minOrderAmount == null || minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 일시는 필수입니다.");
        }
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    /**
     * 주문 금액이 최소 주문 조건을 만족하는지.
     */
    public boolean isApplicable(long orderAmount) {
        return orderAmount >= minOrderAmount;
    }

    /**
     * 주문 금액에 본 쿠폰을 적용했을 때의 할인액.
     * minOrderAmount 미달이면 0 — 호출자는 사용 가능 여부를 별도로 검증해야 한다.
     */
    public long discountFor(long orderAmount) {
        if (!isApplicable(orderAmount)) {
            return 0L;
        }
        return type.discountFor(orderAmount, value);
    }

    /**
     * 기준 시각에 만료되었는지.
     */
    public boolean isExpiredAt(LocalDateTime at) {
        return !at.isBefore(expiredAt);
    }
}
