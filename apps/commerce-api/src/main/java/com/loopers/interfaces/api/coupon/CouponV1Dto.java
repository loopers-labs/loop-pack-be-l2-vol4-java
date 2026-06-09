package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

public class CouponV1Dto {

    public record CreateRequest(
        @NotBlank String name,
        @NotNull CouponType type,
        @NotNull Long value,
        Long minOrderAmount,
        @NotNull LocalDateTime expiredAt
    ) {}

    public record UpdateRequest(
        @NotBlank String name,
        @NotNull CouponType type,
        @NotNull Long value,
        Long minOrderAmount,
        @NotNull LocalDateTime expiredAt
    ) {}

    public record TemplateResponse(
        UUID id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt,
        ZonedDateTime deletedAt
    ) {
        public static TemplateResponse from(CouponInfo info) {
            return new TemplateResponse(
                info.id(),
                info.name(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.createdAt(),
                info.deletedAt()
            );
        }
    }

    public record UserCouponResponse(
        UUID id,
        UUID templateId,
        UserCouponStatus status,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        UUID orderId,
        ZonedDateTime usedAt,
        ZonedDateTime issuedAt
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                info.id(),
                info.templateId(),
                info.status(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.orderId(),
                info.usedAt(),
                info.issuedAt()
            );
        }
    }
}
