package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponAdminInfo;
import com.loopers.application.coupon.CouponIssueAdminInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public class CouponAdminV1Dto {

    public enum CouponTypeDto {
        FIXED, RATE;

        public static CouponTypeDto from(CouponType type) {
            return CouponTypeDto.valueOf(type.name());
        }

        public CouponType toDomain() {
            return CouponType.valueOf(this.name());
        }
    }

    public enum CouponStatusDto {
        AVAILABLE, USED, EXPIRED;

        public static CouponStatusDto from(CouponStatus status) {
            return CouponStatusDto.valueOf(status.name());
        }
    }

    public record CouponTemplateResponse(
        Long id,
        String name,
        CouponTypeDto type,
        int value,
        int minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt
    ) {
        public static CouponTemplateResponse from(CouponAdminInfo info) {
            return new CouponTemplateResponse(
                info.id(),
                info.name(),
                CouponTypeDto.from(info.type()),
                info.value(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.createdAt()
            );
        }
    }

    public record CreateCouponRequest(
        @NotBlank String name,
        @NotNull CouponTypeDto type,
        int value,
        int minOrderAmount,
        @NotNull ZonedDateTime expiredAt
    ) {}

    public record UpdateCouponRequest(
        @NotBlank String name,
        @NotNull CouponTypeDto type,
        int value,
        int minOrderAmount,
        @NotNull ZonedDateTime expiredAt
    ) {}

    public record CouponIssueResponse(
        Long id,
        Long userId,
        Long couponId,
        CouponStatusDto status,
        ZonedDateTime issuedAt
    ) {
        public static CouponIssueResponse from(CouponIssueAdminInfo info) {
            return new CouponIssueResponse(
                info.id(),
                info.userId(),
                info.couponId(),
                CouponStatusDto.from(info.status()),
                info.issuedAt()
            );
        }
    }
}
