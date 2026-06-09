package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "coupon")
public class CouponModel extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private CouponType discountType;

    @Column(name = "discount_value", nullable = false)
    private Long discountValue;

    protected CouponModel() {}

    public CouponModel(String name, CouponType discountType, Long discountValue) {
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        guard();
    }

    @Override
    protected void guard() {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 필수입니다.");
        }
        if (discountType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 타입은 필수입니다.");
        }
        if (discountValue == null || discountValue <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 0보다 커야 합니다.");
        }
        if (discountType == CouponType.RATE && (discountValue < 1 || discountValue > 100)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인은 1 이상 100 이하여야 합니다.");
        }
    }

    public String getName() {
        return name;
    }

    public CouponType getDiscountType() {
        return discountType;
    }

    public Long getDiscountValue() {
        return discountValue;
    }
}
