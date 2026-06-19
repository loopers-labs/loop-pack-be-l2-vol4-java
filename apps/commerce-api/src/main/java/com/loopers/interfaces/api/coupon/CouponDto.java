package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.ZonedDateTime;

public final class CouponDto {

    private CouponDto() {}

    public static final class Template {

        private Template() {}

        public record Response(
            Long id,
            String name,
            CouponType type,
            Long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt
        ) {
            public static Response from(CouponInfo.Template info) {
                return new Response(
                    info.id(),
                    info.name(),
                    info.type(),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt()
                );
            }
        }
    }

    public static final class Issued {

        private Issued() {}

        public record Response(
            Long id,
            Long couponId,
            String userLoginId,
            CouponStatus status,
            ZonedDateTime expiredAt,
            ZonedDateTime usedAt
        ) {
            public static Response from(CouponInfo.Issued info) {
                return new Response(
                    info.id(),
                    info.couponId(),
                    info.userLoginId(),
                    info.status(),
                    info.expiredAt(),
                    info.usedAt()
                );
            }
        }
    }

    public static final class Create {

        private Create() {}

        public static final class V1 {

            private V1() {}

            public record Request(
                @NotBlank
                String name,
                @NotNull
                CouponType type,
                @NotNull
                @Positive
                Long value,
                @PositiveOrZero
                Long minOrderAmount,
                @NotNull
                ZonedDateTime expiredAt
            ) {}

            public record Response(
                Long id,
                String name,
                CouponType type,
                Long value,
                Long minOrderAmount,
                ZonedDateTime expiredAt
            ) {
                public static Response from(CouponInfo.Template info) {
                    CouponDto.Template.Response response = CouponDto.Template.Response.from(info);
                    return new Response(
                        response.id(),
                        response.name(),
                        response.type(),
                        response.value(),
                        response.minOrderAmount(),
                        response.expiredAt()
                    );
                }
            }
        }
    }

    public static final class Update {

        private Update() {}

        public static final class V1 {

            private V1() {}

            public record Request(
                @NotBlank
                String name,
                @NotNull
                CouponType type,
                @NotNull
                @Positive
                Long value,
                @PositiveOrZero
                Long minOrderAmount,
                @NotNull
                ZonedDateTime expiredAt
            ) {}

            public record Response(
                Long id,
                String name,
                CouponType type,
                Long value,
                Long minOrderAmount,
                ZonedDateTime expiredAt
            ) {
                public static Response from(CouponInfo.Template info) {
                    CouponDto.Template.Response response = CouponDto.Template.Response.from(info);
                    return new Response(
                        response.id(),
                        response.name(),
                        response.type(),
                        response.value(),
                        response.minOrderAmount(),
                        response.expiredAt()
                    );
                }
            }
        }
    }
}
