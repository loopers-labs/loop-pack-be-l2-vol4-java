package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class CouponTemplateEntity extends BaseEntity {

    private String name;
    private CouponType type;
    private Long value;
    private Long minOrderAmount;
    private ZonedDateTime expiredAt;

    protected CouponTemplateEntity() {}

    public CouponTemplateEntity(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validateName(name);
        validateType(type);
        validateValue(type, value);
        validateMinOrderAmount(minOrderAmount);
        validateExpiredAt(expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public static CouponTemplateEntity of(Long id, String name, CouponType type, Long value, Long minOrderAmount,
            ZonedDateTime expiredAt, ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        CouponTemplateEntity entity = new CouponTemplateEntity();
        entity.name = name;
        entity.type = type;
        entity.value = value;
        entity.minOrderAmount = minOrderAmount;
        entity.expiredAt = expiredAt;
        entity.reconstruct(id, createdAt, updatedAt, deletedAt);
        return entity;
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

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }

    public boolean isExpired() {
        return ZonedDateTime.now().isAfter(expiredAt);
    }

    public void validateMinimumOrderAmount(Long orderAmount) {
        if (minOrderAmount != null && orderAmount < minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문금액이 최소 주문금액보다 작습니다.");
        }
    }

    public Long calculateDiscount(Long orderAmount) {
        if (type == CouponType.FIXED) {
            return Math.min(value, orderAmount);
        }
        return orderAmount * value / 100;
    }

    public void update(String name, Long minOrderAmount, ZonedDateTime expiredAt) {
        validateName(name);
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 만료일은 필수입니다.");
        }
        this.name = name;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 이름은 필수입니다.");
        }
    }

    private void validateType(CouponType type) {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
    }

    private void validateValue(CouponType type, Long value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 할인 값은 필수입니다.");
        }
        if (type == CouponType.FIXED && value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정액 쿠폰의 할인 값은 양의 정수여야 합니다.");
        }
        if (type == CouponType.RATE && (value < 1 || value > 100)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰의 할인율은 1~100 사이여야 합니다.");
        }
    }

    private void validateMinOrderAmount(Long minOrderAmount) {
        if (minOrderAmount != null && minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문금액은 0 이상이어야 합니다.");
        }
    }

    private void validateExpiredAt(ZonedDateTime expiredAt) {
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 만료일은 필수입니다.");
        }
        if (!expiredAt.isAfter(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 만료일은 미래여야 합니다.");
        }
    }
}
