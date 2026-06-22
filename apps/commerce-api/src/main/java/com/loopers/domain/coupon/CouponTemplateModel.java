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

@Entity
@Table(name = "coupon_templates")
public class CouponTemplateModel extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private CouponType type;

    @Column(name = "value", nullable = false, updatable = false)
    private Long value;

    @Column(name = "min_order_amount", updatable = false)
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false, updatable = false)
    private LocalDateTime expiredAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "is_blocked", nullable = false)
    private boolean isBlocked;

    protected CouponTemplateModel() {}

    public CouponTemplateModel(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 필수입니다.");
        }
        if (type == CouponType.FIXED && (value == null || value <= 0)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정액 할인 금액은 0보다 커야 합니다.");
        }
        if (type == CouponType.RATE && (value == null || value < 1 || value > 100)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인율은 1~100 사이의 자연수여야 합니다.");
        }
        if (minOrderAmount != null && minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 음수일 수 없습니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 일시는 필수입니다.");
        }
        if (!expiredAt.isAfter(LocalDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 일시는 현재 시각 이후여야 합니다.");
        }
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.isActive = true;
    }

    public void update(String name, boolean isActive) {
        this.name = name;
        this.isActive = isActive;
    }

    public void block() {
        this.isBlocked = true;
    }

    public boolean isExpired() {
        return expiredAt.isBefore(LocalDateTime.now());
    }

    public boolean canIssue() {
        return isActive && !isBlocked && !isExpired();
    }

    public boolean isBlocked() {
        return isBlocked;
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

    public boolean isActive() {
        return isActive;
    }
}
