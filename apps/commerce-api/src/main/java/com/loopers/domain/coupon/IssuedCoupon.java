package com.loopers.domain.coupon;

import com.loopers.support.domain.DomainEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class IssuedCoupon extends DomainEntity {

    private Long couponTemplateId;

    private String userId;

    private CouponType type;

    private Long value;

    private Long minOrderAmount;

    private ZonedDateTime expiredAt;

    private CouponStatus status;

    private ZonedDateTime usedAt;

    public IssuedCoupon(Long couponTemplateId, String userId, CouponTemplate template) {
        validateCouponTemplateId(couponTemplateId);
        validateUserId(userId);
        if (template == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿은 필수입니다.");
        }

        this.couponTemplateId = couponTemplateId;
        this.userId = userId;
        this.type = template.getType();
        this.value = template.getValue();
        this.minOrderAmount = template.getMinOrderAmount();
        this.expiredAt = template.getExpiredAt();
        this.status = CouponStatus.AVAILABLE;
    }

    public static IssuedCoupon reconstruct(
        Long id,
        Long couponTemplateId,
        String userId,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        CouponStatus status,
        ZonedDateTime usedAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        IssuedCoupon issuedCoupon = new IssuedCoupon();
        issuedCoupon.validateCouponTemplateId(couponTemplateId);
        issuedCoupon.validateUserId(userId);
        issuedCoupon.couponTemplateId = couponTemplateId;
        issuedCoupon.userId = userId;
        issuedCoupon.type = type;
        issuedCoupon.value = value;
        issuedCoupon.minOrderAmount = minOrderAmount;
        issuedCoupon.expiredAt = expiredAt;
        issuedCoupon.status = status;
        issuedCoupon.usedAt = usedAt;
        issuedCoupon.assignMetadata(id, createdAt, updatedAt, deletedAt);
        return issuedCoupon;
    }

    private IssuedCoupon() {}

    public Long use(String requestedUserId, Long originalAmount, ZonedDateTime now) {
        if (!userId.equals(requestedUserId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "다른 사용자의 쿠폰은 사용할 수 없습니다.");
        }
        if (getStatus(now) != CouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
        }

        Long discountAmount = CouponTemplate.calculateDiscount(type, value, minOrderAmount, originalAmount);
        this.status = CouponStatus.USED;
        this.usedAt = now;
        return discountAmount;
    }

    public void restore() {
        if (status == CouponStatus.USED) {
            this.status = CouponStatus.AVAILABLE;
            this.usedAt = null;
        }
    }

    public CouponStatus getStatus(ZonedDateTime now) {
        if (status == CouponStatus.AVAILABLE && !expiredAt.isAfter(now)) {
            return CouponStatus.EXPIRED;
        }
        return status;
    }

    public Long getCouponTemplateId() {
        return couponTemplateId;
    }

    public String getUserId() {
        return userId;
    }

    public CouponType getType() {
        return type;
    }

    public Long getValue() {
        return value;
    }

    public Long getMinOrderAmount() {
        return minOrderAmount;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }

    public CouponStatus getStoredStatus() {
        return status;
    }

    public ZonedDateTime getUsedAt() {
        return usedAt;
    }

    private void validateCouponTemplateId(Long value) {
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 ID는 필수입니다.");
        }
    }

    private void validateUserId(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }
}
