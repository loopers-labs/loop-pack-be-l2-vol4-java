package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class CouponAdminV1Dto {

    public enum CouponTypeDto {
        FIXED, RATE;

        public static CouponTypeDto from(CouponType domainType) {
            return valueOf(domainType.name());
        }

        public CouponType toDomain() {
            return CouponType.valueOf(this.name());
        }
    }

    public record CouponCreateRequest(
            String name,
            CouponTypeDto type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt,
            int totalQuantity
    ) {}

    public record CouponUpdateRequest(
            String name,
            CouponTypeDto type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt,
            int totalQuantity
    ) {}

    public record CouponResponse(
            Long id,
            String name,
            CouponTypeDto type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt,
            int totalQuantity,
            int issuedQuantity,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                    info.id(),
                    info.name(),
                    CouponTypeDto.from(info.type()),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt(),
                    info.totalQuantity(),
                    info.issuedQuantity(),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }

    public record IssuedCouponResponse(
            Long id,
            Long couponTemplateId,
            Long userId,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static IssuedCouponResponse from(IssuedCouponInfo info) {
            return new IssuedCouponResponse(
                    info.id(),
                    info.couponTemplateId(),
                    info.userId(),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }
}
