package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponCreateCommand;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.CouponUpdateCommand;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record CouponCreateRequest(
        @NotBlank String name,
        @NotNull CouponType type,
        @Min(1) int value,
        Integer minOrderAmount,
        @NotNull ZonedDateTime expiredAt
    ) {
        public CouponCreateCommand toCommand() {
            return new CouponCreateCommand(name, type, value, minOrderAmount, expiredAt);
        }
    }

    public record CouponUpdateRequest(
        @NotBlank String name,
        @NotNull CouponType type,
        @Min(1) int value,
        Integer minOrderAmount,
        @NotNull ZonedDateTime expiredAt
    ) {
        public CouponUpdateCommand toCommand() {
            return new CouponUpdateCommand(name, type, value, minOrderAmount, expiredAt);
        }
    }

    public record CouponResponse(
        Long id,
        String name,
        CouponType type,
        int value,
        Integer minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                info.id(), info.name(), info.type(), info.value(),
                info.minOrderAmount(), info.expiredAt(), info.createdAt()
            );
        }
    }

    public record UserCouponResponse(
        Long id,
        Long userId,
        CouponResponse coupon,
        UserCouponStatus status,
        ZonedDateTime createdAt
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                info.id(), info.userId(),
                CouponResponse.from(info.coupon()),
                info.status(),
                info.createdAt()
            );
        }
    }
}
