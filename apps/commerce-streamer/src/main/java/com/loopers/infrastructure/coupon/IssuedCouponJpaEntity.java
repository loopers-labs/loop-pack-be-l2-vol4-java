package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "issued_coupon",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_issued_coupon_coupon_user",
        columnNames = {"coupon_id", "user_login_id"}
    )
)
public class IssuedCouponJpaEntity extends BaseEntity {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_login_id", nullable = false)
    private String userLoginId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private ZonedDateTime expiredAt;

    @Column
    private ZonedDateTime usedAt;

    protected IssuedCouponJpaEntity() {
    }

    private IssuedCouponJpaEntity(Long couponId, String userLoginId, ZonedDateTime expiredAt) {
        this.couponId = couponId;
        this.userLoginId = userLoginId;
        this.status = "AVAILABLE";
        this.expiredAt = expiredAt;
    }

    public static IssuedCouponJpaEntity issue(Long couponId, String userLoginId, ZonedDateTime expiredAt) {
        return new IssuedCouponJpaEntity(couponId, userLoginId, expiredAt);
    }
}
