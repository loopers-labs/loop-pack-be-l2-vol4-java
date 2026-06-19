package com.loopers.interfaces.api.admin.coupon;

import com.loopers.application.coupon.CouponCommand;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.UserCouponInfo;

import java.time.ZonedDateTime;

public class CouponAdminV1Dto {

    public record CouponRequest(String name, String type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        public CouponCommand.Create toCreateCommand() {
            return new CouponCommand.Create(name, type, value != null ? value : 0L, minOrderAmount, expiredAt);
        }

        public CouponCommand.Update toUpdateCommand() {
            return new CouponCommand.Update(name, type, value != null ? value : 0L, minOrderAmount, expiredAt);
        }
    }

    public record CouponResponse(
        Long id,
        String name,
        String type,
        long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                info.id(), info.name(), info.type(), info.value(), info.minOrderAmount(), info.expiredAt()
            );
        }
    }

    public record IssuedCouponResponse(
        Long id,
        Long userId,
        String status,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt,
        ZonedDateTime expiredAt
    ) {
        public static IssuedCouponResponse from(UserCouponInfo info) {
            return new IssuedCouponResponse(
                info.id(), info.userId(), info.status(), info.issuedAt(), info.usedAt(), info.expiredAt()
            );
        }
    }
}
