package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "issued_coupon")
public class IssuedCouponModel extends BaseEntity {

    private Long couponTemplateId;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private CouponStatus status;

    protected IssuedCouponModel() {
    }

    public IssuedCouponModel(Long couponTemplateId, Long userId) {
        if (couponTemplateId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "couponTemplateId는 필수입니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 필수입니다.");
        }
        this.couponTemplateId = couponTemplateId;
        this.userId = userId;
        this.status = CouponStatus.AVAILABLE;
    }

}
