package com.loopers.interfaces.api.admin.coupon;

import com.loopers.application.coupon.CouponCommand;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.DiscountType;

import java.time.ZonedDateTime;

public class CouponAdminV1Dto {

    // ===== 요청 =====
    public record CreateRequest(
            String name,
            DiscountType type,         // FIXED | RATE
            long value,
            Long minOrderAmount,       // nullable
            ZonedDateTime expiredAt
    ) {
        public CouponCommand.Create toCommand() {
            return new CouponCommand.Create(name, type, value, minOrderAmount, expiredAt);
        }
    }

    public record UpdateRequest(
            String name,
            DiscountType type,
            long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt
    ) {
        public CouponCommand.Update toCommand() {
            return new CouponCommand.Update(name, type, value, minOrderAmount, expiredAt);
        }
    }

    // ===== 응답 =====
    public record TemplateResponse(
            Long id,
            String name,
            DiscountType type,
            long value,
            long minOrderAmount,
            ZonedDateTime expiredAt
    ) {
        public static TemplateResponse from(CouponTemplateInfo info) {
            return new TemplateResponse(
                    info.id(), info.name(), info.type(), info.value(), info.minOrderAmount(), info.expiredAt());
        }
    }

    public record IssueResponse(
            Long userCouponId,
            Long couponId,
            CouponStatus status,
            ZonedDateTime expiredAt
    ) {
        public static IssueResponse from(CouponInfo info) {
            return new IssueResponse(
                    info.userCouponId(), info.couponId(), info.status(), info.expiredAt());
        }
    }
}
