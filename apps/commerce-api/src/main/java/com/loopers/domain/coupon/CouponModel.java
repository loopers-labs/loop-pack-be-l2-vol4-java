package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.Guard;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CouponModel extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Embedded
    private CouponDiscount discount;

    @Embedded
    private CouponExpiry expiry;

    public CouponModel(String name, CouponDiscount discount, CouponExpiry expiry) {
        Guard.notBlank(name, "쿠폰 이름은 비어있을 수 없습니다.");
        Guard.maxLength(name, 100, "쿠폰 이름은 100자 이하여야 합니다.");
        Guard.notNull(discount, "쿠폰 할인 정보는 필수입니다.");
        Guard.notNull(expiry, "쿠폰 만료 정보는 필수입니다.");
        this.name = name;
        this.discount = discount;
        this.expiry = expiry;
    }

    public boolean isExpired() {
        return expiry.isExpired();
    }

    public void validateNotExpired() {
        if (isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
    }

    public void extendExpiredAt(ZonedDateTime newExpiredAt) {
        this.expiry = expiry.extend(newExpiredAt);
    }

    public void updateName(String newName) {
        Guard.notBlank(newName, "쿠폰 이름은 비어있을 수 없습니다.");
        Guard.maxLength(newName, 100, "쿠폰 이름은 100자 이하여야 합니다.");
        this.name = newName;
    }

    public void validateDeletable() {
        if (!isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰만 삭제할 수 있습니다.");
        }
    }

    public long calculateDiscount(long orderAmount) {
        return discount.calculateDiscount(orderAmount);
    }
}
