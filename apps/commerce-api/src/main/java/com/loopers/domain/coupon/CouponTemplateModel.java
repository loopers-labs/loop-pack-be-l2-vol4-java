package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CouponTemplateModel extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private Long value;

    @Column(name = "min_order_amount")
    private Long minOrderAmount; // nullable = 조건 없음

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    public CouponTemplateModel(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, type, value, expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public void update(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, type, value, expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public boolean isExpired(ZonedDateTime now) {
        return expiredAt.isBefore(now);
    }

    public boolean meetsMinOrderAmount(long originalAmount) {
        return minOrderAmount == null || originalAmount >= minOrderAmount;
    }

    public long calculateDiscount(long originalAmount) {
        long raw = type.rawDiscount(value, originalAmount);
        return Math.min(raw, originalAmount); // 원금 초과 불가 (cap)
    }

    private void validate(String name, CouponType type, Long value, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일시는 필수입니다.");
        }
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인값은 필수입니다.");
        }
        if (type == CouponType.FIXED && value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정액 할인값은 0보다 커야 합니다.");
        }
        if (type == CouponType.RATE && (value < 1 || value > 100)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인값은 1~100 범위여야 합니다.");
        }
    }
}
