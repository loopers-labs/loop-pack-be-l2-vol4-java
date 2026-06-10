package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.application.coupon.MyIssuedCouponInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class CouponV1Dto {

    public enum CouponTypeDto {
        FIXED, RATE;

        public static CouponTypeDto from(CouponType type) {
            return valueOf(type.name());
        }
    }

    public enum CouponStatusDto {
        AVAILABLE, USED, EXPIRED
    }

    public record MyIssuedCouponResponse(
            Long couponId,
            String name,
            CouponTypeDto type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt,
            CouponStatusDto status
    ) {
        public static MyIssuedCouponResponse from(MyIssuedCouponInfo info) {
            return new MyIssuedCouponResponse(
                    info.id(),
                    info.name(),
                    CouponTypeDto.from(info.type()),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt(),
                    info.status() == CouponStatus.USED
                            ? CouponStatusDto.USED
                            : ZonedDateTime.now().isAfter(info.expiredAt()) ? CouponStatusDto.EXPIRED : CouponStatusDto.AVAILABLE
            );
        }
    }

    public record IssueResponse(
            Long id,
            Long couponTemplateId,
            Long userId,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static IssueResponse from(IssuedCouponInfo info) {
            return new IssueResponse(
                    info.id(),
                    info.couponTemplateId(),
                    info.userId(),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }
}
