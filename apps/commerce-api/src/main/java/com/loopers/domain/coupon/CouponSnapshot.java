package com.loopers.domain.coupon;

import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

/**
 * 발급 시점의 쿠폰 정책 사본 (VO). 발급된 쿠폰은 사용자와의 계약이므로
 * 템플릿 수정/삭제와 무관하게 이 스냅샷 기준으로 할인을 계산한다.
 * 임베드 시 컬럼명은 사용처 엔티티에서 {@code @AttributeOverride} 로 지정한다.
 */
@Embeddable
@Access(AccessType.FIELD)
public class CouponSnapshot {

    private String name;

    @Enumerated(EnumType.STRING)
    private CouponType type;

    private long value;

    private Long minOrderAmount;

    protected CouponSnapshot() {}

    public CouponSnapshot(String name, CouponType type, long value, Long minOrderAmount) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (type == CouponType.FIXED && value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정액 할인 금액은 1원 이상이어야 합니다.");
        }
        if (type == CouponType.RATE && (value < 1 || value > 100)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인율은 1~100% 사이여야 합니다.");
        }
        if (minOrderAmount != null && minOrderAmount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 1원 이상이어야 합니다.");
        }
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
    }

    public Money calculateDiscount(Money orderAmount) {
        if (orderAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액은 필수입니다.");
        }
        if (minOrderAmount != null && orderAmount.getAmount() < minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 않아 쿠폰을 적용할 수 없습니다.");
        }
        return switch (type) {
            case FIXED -> Money.of(Math.min(value, orderAmount.getAmount()));
            case RATE -> Money.of(orderAmount.getAmount() * value / 100); // long 나눗셈 → 원 단위 절사
        };
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
        return minOrderAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CouponSnapshot that = (CouponSnapshot) o;
        return value == that.value && name.equals(that.name)
            && type == that.type && Objects.equals(minOrderAmount, that.minOrderAmount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, value, minOrderAmount);
    }
}
