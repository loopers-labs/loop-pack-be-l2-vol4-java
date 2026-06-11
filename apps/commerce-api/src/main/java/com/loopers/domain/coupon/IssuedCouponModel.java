package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "issued_coupon")
public class IssuedCouponModel extends BaseEntity {

    private Long couponTemplateId;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private CouponStatus status;

    @Version
    private long version;

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

    public void validateOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "해당 쿠폰에 접근할 수 없습니다.");
        }
    }

    public void use() {
        if (this.status == CouponStatus.USED) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.");
        }
        this.status = CouponStatus.USED;
    }

}
