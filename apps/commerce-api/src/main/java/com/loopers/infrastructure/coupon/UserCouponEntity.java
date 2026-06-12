package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.UserCouponModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_coupons")
public class UserCouponEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponStatus status;

    @Version
    private Long version;

    private UserCouponEntity(Long userId, Long couponId, CouponStatus status) {
        this.userId = userId;
        this.couponId = couponId;
        this.status = status;
    }

    public void applyUpdate(CouponStatus status, Long version) {
        this.status = status;
        this.version = version;
    }

    public static UserCouponEntity from(UserCouponModel model) {
        return new UserCouponEntity(
            model.getUserId(),
            model.getCouponId(),
            model.getStatus()
        );
    }

    public UserCouponModel toDomain() {
        return new UserCouponModel(
            getId(),
            userId,
            couponId,
            status,
            version,
            getCreatedAt(),
            getUpdatedAt()
        );
    }
}
