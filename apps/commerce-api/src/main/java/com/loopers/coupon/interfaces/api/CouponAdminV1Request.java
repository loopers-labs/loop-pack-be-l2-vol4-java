package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.application.CouponCommand;
import com.loopers.coupon.domain.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.ZonedDateTime;

public class CouponAdminV1Request {

    public record Create(
            @NotBlank(message = "쿠폰 이름은 필수입니다.")
            @Size(max = 100, message = "쿠폰 이름은 100자 이내여야 합니다.")
            String name,

            @NotNull(message = "쿠폰 타입은 필수입니다.")
            CouponType type,

            @Positive(message = "할인 값은 0보다 커야 합니다.")
            long value,

            @PositiveOrZero(message = "최소 주문 금액은 0 이상이어야 합니다.")
            Long minOrderAmount,

            @NotNull(message = "만료 시각은 필수입니다.")
            ZonedDateTime expiredAt
    ) {
        public CouponCommand.Create toCommand() {
            return new CouponCommand.Create(name, type, value, minOrderAmount, expiredAt);
        }
    }

    public record Update(
            @NotBlank(message = "쿠폰 이름은 필수입니다.")
            @Size(max = 100, message = "쿠폰 이름은 100자 이내여야 합니다.")
            String name,

            @NotNull(message = "쿠폰 타입은 필수입니다.")
            CouponType type,

            @Positive(message = "할인 값은 0보다 커야 합니다.")
            long value,

            @PositiveOrZero(message = "최소 주문 금액은 0 이상이어야 합니다.")
            Long minOrderAmount,

            @NotNull(message = "만료 시각은 필수입니다.")
            ZonedDateTime expiredAt
    ) {
        public CouponCommand.Update toCommand(Long couponId) {
            return new CouponCommand.Update(couponId, name, type, value, minOrderAmount, expiredAt);
        }
    }
}
