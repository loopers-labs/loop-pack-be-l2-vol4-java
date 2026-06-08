package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.MyCouponInfo;
import com.loopers.domain.coupon.model.CouponStatus;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.model.CouponType;
import com.loopers.domain.coupon.model.IssuedCoupon;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    // ADMIN
    public record TemplateCreateRequest(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
    ) {}

    public record TemplateUpdateRequest(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
    ) {}

    public record TemplateResponse(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt
    ) {
        public static TemplateResponse from(CouponTemplate template) {
            return new TemplateResponse(
                template.getId(),
                template.getName(),
                template.getType(),
                template.getValue(),
                template.getMinOrderAmount(),
                template.getExpiredAt(),
                template.getCreatedAt()
            );
        }
    }

    public record IssuedCouponResponse(
        Long id,
        Long couponTemplateId,
        Long memberId,
        CouponStatus status,
        ZonedDateTime createdAt
    ) {
        public static IssuedCouponResponse from(IssuedCoupon issuedCoupon) {
            return new IssuedCouponResponse(
                issuedCoupon.getId(),
                issuedCoupon.getCouponTemplateId(),
                issuedCoupon.getMemberId(),
                issuedCoupon.getStatus(),
                issuedCoupon.getCreatedAt()
            );
        }
    }

    // 대고객
    public record MyCouponResponse(
        Long issuedCouponId,
        String couponName,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        CouponStatus status
    ) {
        public static MyCouponResponse from(MyCouponInfo info) {
            return new MyCouponResponse(
                info.issuedCouponId(),
                info.couponName(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.status()
            );
        }
    }
}
