package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.vo.CouponOwner;
import com.loopers.domain.coupon.vo.CouponTemplateId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "user_coupon",
    indexes = @Index(name = "idx_user_coupon_status_template", columnList = "status, coupon_template_id"),
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_coupon_user_template",
        columnNames = {"user_id", "coupon_template_id"}
    )
)
public class UserCoupon extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "user_id", nullable = false))
    private CouponOwner owner;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "coupon_template_id", nullable = false))
    private CouponTemplateId couponTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserCouponStatus status;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    private UserCoupon(Long userId, Long couponTemplateId) {
        this.owner = CouponOwner.of(userId);
        this.couponTemplateId = CouponTemplateId.of(couponTemplateId);
        this.status = UserCouponStatus.AVAILABLE;
    }

    public static UserCoupon issue(Long userId, Long couponTemplateId) {
        return new UserCoupon(userId, couponTemplateId);
    }

    public Long getUserId() {
        return owner.userId();
    }

    public Long getCouponTemplateId() {
        return couponTemplateId.value();
    }

    public boolean isIssuedTo(Long userId) {
        return owner.isSameUser(userId);
    }

    public boolean isAvailable() {
        return status == UserCouponStatus.AVAILABLE;
    }

    public void use(Long userId, ZonedDateTime usedAt) {
        validateOwner(userId);
        validateUsedAt(usedAt);
        validateAvailable();

        this.status = UserCouponStatus.USED;
        this.usedAt = usedAt;
    }

    public void expire() {
        if (status == UserCouponStatus.AVAILABLE) {
            this.status = UserCouponStatus.EXPIRED;
        }
    }

    private void validateOwner(Long userId) {
        if (!owner.isSameUser(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 사용자의 쿠폰은 사용할 수 없습니다.");
        }
    }

    private static void validateUsedAt(ZonedDateTime usedAt) {
        if (usedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 사용 일시는 비어있을 수 없습니다.");
        }
    }

    private void validateAvailable() {
        if (status != UserCouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.CONFLICT, "사용할 수 없는 쿠폰입니다.");
        }
    }
}
