package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;

/** CouponIssueRequestModel ↔ CouponIssueRequestEntity 변환 (방식2, 정적 유틸). */
public final class CouponIssueRequestEntityMapper {

    private CouponIssueRequestEntityMapper() {}

    public static CouponIssueRequestEntity toEntity(CouponIssueRequestModel model) {
        return new CouponIssueRequestEntity(
                model.getId(),
                model.getUserId(),
                model.getCouponId(),
                model.getStatus(),
                model.getRequestedAt(),
                model.getProcessedAt()
        );
    }

    public static CouponIssueRequestModel toDomain(CouponIssueRequestEntity entity) {
        return CouponIssueRequestModel.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getCouponId(),
                entity.getStatus(),
                entity.getRequestedAt(),
                entity.getProcessedAt()
        );
    }
}
