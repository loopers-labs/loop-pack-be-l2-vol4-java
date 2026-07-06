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
            ZonedDateTime expiredAt,

            // 선착순 발급 한도(선택). null 이면 수량 무제한.
            @Positive(message = "발급 수량은 0보다 커야 합니다.")
            Long quantity
    ) {
        // 수량 무제한 생성용 — 기존 호출부 호환
        public Create(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
            this(name, type, value, minOrderAmount, expiredAt, null);
        }

        public CouponCommand.Create toCommand() {
            return new CouponCommand.Create(name, type, value, minOrderAmount, expiredAt, quantity);
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
