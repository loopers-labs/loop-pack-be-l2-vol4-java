package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "user_coupon")
public class UserCouponModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserCouponStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    protected UserCouponModel() {}

    public UserCouponModel(Long userId, Long couponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저는 필수입니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰은 필수입니다.");
        }
        this.userId = userId;
        this.couponId = couponId;
        this.status = UserCouponStatus.AVAILABLE;
    }

    public void use() {
        if (this.status != UserCouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
        }
        this.status = UserCouponStatus.USED;
    }

    public void expire() {
        if (this.status != UserCouponStatus.AVAILABLE) {
            return;
        }
        this.status = UserCouponStatus.EXPIRED;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public UserCouponStatus getStatus() {
        return status;
    }
}
