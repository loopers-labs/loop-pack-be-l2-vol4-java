package com.loopers.interfaces.apiadmin.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.enums.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;

public class CouponAdminV1Dto {

    public record RegisterRequest(
            @NotBlank(message = "쿠폰 이름은 빈값이 들어올 수 없습니다.") String name,
            @NotNull(message = "쿠폰 타입은 필수입니다.") CouponType type,
            @NotNull(message = "쿠폰 할인 값은 필수입니다.") Long value,
            Long minOrderAmount,
            @NotNull(message = "쿠폰 만료일은 필수입니다.") ZonedDateTime expiredAt
    ) {}

    public record UpdateRequest(
            String name,
            ZonedDateTime expiredAt
    ) {}

    public record CouponResponse(
            Long id,
            String name,
            String type,
            Long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                    info.id(),
                    info.name(),
                    info.type(),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt()
            );
        }

        public static Page<CouponResponse> from(Page<CouponInfo> page) {
            return page.map(CouponResponse::from);
        }
    }

    public record IssueResponse(
            Long id,
            Long userId,
            Long couponId,
            String status,
            ZonedDateTime usedAt
    ) {
        public static IssueResponse from(UserCouponInfo info) {
            return new IssueResponse(
                    info.id(),
                    info.userId(),
                    info.coupon().id(),
                    info.status(),
                    info.usedAt()
            );
        }

        public static Page<IssueResponse> from(Page<UserCouponInfo> page) {
            return page.map(IssueResponse::from);
        }
    }
}
