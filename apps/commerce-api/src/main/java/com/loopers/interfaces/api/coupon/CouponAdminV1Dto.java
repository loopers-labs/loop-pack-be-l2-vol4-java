package com.loopers.interfaces.api.coupon;

import java.time.ZonedDateTime;

import com.loopers.application.coupon.CouponCreateInfo;
import com.loopers.domain.coupon.DiscountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CouponAdminV1Dto {

    public record CreateRequest(
        @NotBlank(message = "쿠폰 이름은 null이거나 빈 값일 수 없습니다.")
        String name,

        @NotNull(message = "할인 타입은 null일 수 없습니다.")
        DiscountType discountType,

        @NotNull(message = "할인 값은 null일 수 없습니다.")
        Integer discountValue,

        Integer minOrderAmount,

        @NotNull(message = "만료 시각은 null일 수 없습니다.")
        ZonedDateTime expiredAt
    ) {
    }

    public record CreateResponse(Long couponId) {

        public static CreateResponse from(CouponCreateInfo couponCreateInfo) {
            return new CreateResponse(couponCreateInfo.couponId());
        }
    }
}
