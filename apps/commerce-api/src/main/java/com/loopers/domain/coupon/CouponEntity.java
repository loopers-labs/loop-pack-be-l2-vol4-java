package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class CouponEntity extends BaseEntity {

    private String couponTemplateId;
    private String userId;
    private CouponStatus status;

    protected CouponEntity() {}

    public CouponEntity(String couponTemplateId, String userId) {
        if (couponTemplateId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        this.couponTemplateId = couponTemplateId;
        this.userId = userId;
        this.status = CouponStatus.AVAILABLE;
    }

    public static CouponEntity of(String id, String couponTemplateId, String userId, CouponStatus status,
            ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        CouponEntity entity = new CouponEntity();
        entity.couponTemplateId = couponTemplateId;
        entity.userId = userId;
        entity.status = status;
        entity.reconstruct(id, createdAt, updatedAt, deletedAt);
        return entity;
    }

    public String getCouponTemplateId() {
        return couponTemplateId;
    }

    public String getUserId() {
        return userId;
    }

    public CouponStatus getStatus() {
        return status;
    }

    public void use() {
        if (status != CouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용 가능한 쿠폰이 아닙니다.");
        }
        this.status = CouponStatus.USED;
    }

    public CouponStatus resolveStatus(ZonedDateTime expiredAt) {
        if (status == CouponStatus.AVAILABLE && ZonedDateTime.now().isAfter(expiredAt)) {
            return CouponStatus.EXPIRED;
        }
        return status;
    }

    public boolean isOwnedBy(String userId) {
        return this.userId.equals(userId);
    }
}
