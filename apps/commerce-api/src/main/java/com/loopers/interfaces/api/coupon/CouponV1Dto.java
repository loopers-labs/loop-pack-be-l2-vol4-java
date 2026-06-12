package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record IssueCouponResponse(
            Long id,
            Long couponTemplateId,
            Long memberId,
            CouponStatus status,
            ZonedDateTime expiredAt
    ) {
        public static IssueCouponResponse from(UserCoupon userCoupon) {
            return new IssueCouponResponse(
                    userCoupon.getId(),
                    userCoupon.getCouponTemplateId(),
                    userCoupon.getMemberId(),
                    userCoupon.getStatus(),
                    userCoupon.getExpiredAt()
            );
        }
    }

    public record UserCouponResponse(
            Long id,
            Long couponTemplateId,
            CouponStatus status,
            ZonedDateTime expiredAt,
            ZonedDateTime usedAt
    ) {
        public static UserCouponResponse from(UserCoupon userCoupon) {
            return new UserCouponResponse(
                    userCoupon.getId(),
                    userCoupon.getCouponTemplateId(),
                    userCoupon.getStatus(),
                    userCoupon.getExpiredAt(),
                    userCoupon.getUsedAt()
            );
        }
    }

    public record CouponTemplateResponse(
            Long id,
            String name,
            CouponType type,
            Long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt
    ) {
        public static CouponTemplateResponse from(CouponTemplate template) {
            return new CouponTemplateResponse(
                    template.getId(),
                    template.getName(),
                    template.getType(),
                    template.getValue(),
                    template.getMinOrderAmount(),
                    template.getExpiredAt()
            );
        }
    }

    public record CreateCouponTemplateRequest(
            String name,
            CouponType type,
            Long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt
    ) {}

    public record CouponIssueResponse(
            Long id,
            Long memberId,
            CouponStatus status,
            ZonedDateTime expiredAt,
            ZonedDateTime usedAt
    ) {
        public static CouponIssueResponse from(UserCoupon userCoupon) {
            return new CouponIssueResponse(
                    userCoupon.getId(),
                    userCoupon.getMemberId(),
                    userCoupon.getStatus(),
                    userCoupon.getExpiredAt(),
                    userCoupon.getUsedAt()
            );
        }
    }

    public record UpdateCouponTemplateRequest(
            String name,
            CouponType type,
            Long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt
    ) {}
}
