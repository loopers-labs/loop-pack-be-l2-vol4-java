package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class CouponAdminV1Dto {

    public record CouponCreateRequest(
            @NotBlank(message = "쿠폰 이름은 필수입니다.") String name,
            @NotNull(message = "쿠폰 타입은 필수입니다.") CouponType type,
            @NotNull(message = "할인 값은 필수입니다.") BigDecimal value,
            BigDecimal minOrderAmount,
            @NotNull(message = "만료일은 필수입니다.") ZonedDateTime expiredAt
    ) {}

    public record CouponUpdateRequest(
            @NotBlank(message = "쿠폰 이름은 필수입니다.") String name,
            @NotNull(message = "쿠폰 타입은 필수입니다.") CouponType type,
            @NotNull(message = "할인 값은 필수입니다.") BigDecimal value,
            BigDecimal minOrderAmount,
            @NotNull(message = "만료일은 필수입니다.") ZonedDateTime expiredAt
    ) {}

    public record CouponResponse(
            Long id,
            String name,
            CouponType type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt
    ) {
        public static CouponResponse from(CouponInfo.Detail info) {
            return new CouponResponse(
                info.id(), info.name(), info.type(),
                info.value(), info.minOrderAmount(), info.expiredAt()
            );
        }
    }

    public record IssuedCouponResponse(
            Long id,
            Long couponId,
            Long userId,
            CouponStatus status,
            ZonedDateTime usedAt,
            ZonedDateTime createdAt
    ) {
        public static IssuedCouponResponse from(CouponInfo.Issued info) {
            return new IssuedCouponResponse(
                info.id(), info.couponId(), info.userId(),
                info.status(), info.usedAt(), info.createdAt()
            );
        }
    }
}
