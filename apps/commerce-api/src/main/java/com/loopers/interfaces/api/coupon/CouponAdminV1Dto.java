package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.domain.coupon.CouponStatus;
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

    public enum CouponStatusDto {
        AVAILABLE, USED, EXPIRED;

        public static CouponStatusDto from(CouponStatus domainStatus) {
            return valueOf(domainStatus.name());
        }
    }

    public record CouponCreateRequest(
            String name,
            CouponTypeDto type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt
    ) {}

    public record CouponUpdateRequest(
            String name,
            CouponTypeDto type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt
    ) {}

    public record CouponResponse(
            Long id,
            String name,
            CouponTypeDto type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt,
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
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }

    public record IssuedCouponResponse(
            Long id,
            Long couponTemplateId,
            Long userId,
            CouponStatusDto status,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static IssuedCouponResponse from(IssuedCouponInfo info) {
            return new IssuedCouponResponse(
                    info.id(),
                    info.couponTemplateId(),
                    info.userId(),
                    CouponStatusDto.from(info.status()),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }
}
