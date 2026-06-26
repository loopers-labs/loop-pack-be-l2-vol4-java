package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.enums.UserCouponStatus;
import com.loopers.support.Guard;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "user_coupons",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_coupon", columnNames = {"user_id", "coupon_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class UserCouponModel extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private CouponModel coupon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserCouponStatus status;

    @Column
    private ZonedDateTime usedAt;

    public UserCouponModel(Long userId, CouponModel coupon) {
        Guard.notNull(userId, "유저 ID는 필수입니다.");
        Guard.notNull(coupon, "쿠폰은 필수입니다.");
        this.userId = userId;
        this.coupon = coupon;
        this.status = UserCouponStatus.ISSUED;
    }

    public void use() {
        if (status != UserCouponStatus.ISSUED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        this.status = UserCouponStatus.USED;
        this.usedAt = ZonedDateTime.now();
    }

    public void revert() {
        if (status != UserCouponStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용된 쿠폰만 복구할 수 있습니다.");
        }
        this.status = coupon.getExpiry().isExpired() ? UserCouponStatus.EXPIRED : UserCouponStatus.ISSUED;
        this.usedAt = null;
    }
}
