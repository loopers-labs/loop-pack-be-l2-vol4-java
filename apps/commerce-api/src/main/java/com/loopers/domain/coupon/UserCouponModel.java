package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_coupons_user_template", columnNames = {"user_id", "template_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class UserCouponModel extends BaseEntity {

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID userId;

    @Column(name = "template_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserCouponStatus status;

    // 발급 시점 스냅샷 (템플릿 수정/삭제와 독립)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private Long value;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "order_id", columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    public UserCouponModel(UUID userId, UUID templateId, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        this.userId = userId;
        this.templateId = templateId;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.status = UserCouponStatus.AVAILABLE;
    }

    public boolean isExpired(ZonedDateTime now) {
        return expiredAt.isBefore(now);
    }

    /** 저장 status(AVAILABLE/USED) + 만료(동적)를 합친 표시 상태 */
    public UserCouponStatus displayStatus(ZonedDateTime now) {
        if (status == UserCouponStatus.USED) {
            return UserCouponStatus.USED;
        }
        return isExpired(now) ? UserCouponStatus.EXPIRED : UserCouponStatus.AVAILABLE;
    }

    public boolean meetsMinOrderAmount(long originalAmount) {
        return minOrderAmount == null || originalAmount >= minOrderAmount;
    }

    public long calculateDiscount(long originalAmount) {
        long raw = type.rawDiscount(value, originalAmount);
        return Math.min(raw, originalAmount); // 원금 초과 불가 (cap)
    }

    public void use(UUID orderId) {
        if (status != UserCouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.");
        }
        this.status = UserCouponStatus.USED;
        this.orderId = orderId;
        this.usedAt = ZonedDateTime.now();
    }

    /** 결제 실패/만료 시 사용 취소 — USED → AVAILABLE. 멱등. */
    public void release() {
        if (status == UserCouponStatus.USED) {
            this.status = UserCouponStatus.AVAILABLE;
            this.orderId = null;
            this.usedAt = null;
        }
    }
}
