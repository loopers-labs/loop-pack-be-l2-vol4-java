package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "user_coupons",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_coupon", columnNames = {"user_id", "coupon_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCouponModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "used", nullable = false)
    private boolean used;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    private UserCouponModel(Long userId, Long couponId) {
        this.userId = userId;
        this.couponId = couponId;
        this.used = false;
        this.usedAt = null;
    }

    public static UserCouponModel of(Long userId, Long couponId) {
        return new UserCouponModel(userId, couponId);
    }

    public void use(ZonedDateTime now) {
        if (used) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.");
        }
        this.used = true;
        this.usedAt = now;
    }

    public void cancel() {
        this.used = false;
        this.usedAt = null;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof UserCouponModel that)) {
            return false;
        }

        Long id = getId();
        Long otherId = that.getId();
        if (id == null || id == 0L || otherId == null || otherId == 0L) {
            return false;
        }
        return Objects.equals(id, otherId);
    }

    @Override
    public int hashCode() {
        return UserCouponModel.class.hashCode();
    }
}