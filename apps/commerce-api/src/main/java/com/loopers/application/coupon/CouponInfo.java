package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class CouponInfo {

    public record Detail(
            Long id,
            String name,
            CouponType type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt
    ) {
        public static Detail from(Coupon coupon) {
            return new Detail(coupon.getId(), coupon.getName(), coupon.getType(),
                coupon.getValue(), coupon.getMinOrderAmount(), coupon.getExpiredAt());
        }
    }

    public record MyCoupon(
            Long issuedCouponId,
            Long couponId,
            ZonedDateTime expiredAt,
            CouponStatus status
    ) {
        public static MyCoupon of(IssuedCoupon issued) {
            // DB의 status는 만료돼도 AVAILABLE로 남아있으므로, 조회 시점에 expiredAt을 비교해 EXPIRED 여부를 판단한다.
            CouponStatus effectiveStatus;
            if (issued.getStatus() == CouponStatus.USED) {
                effectiveStatus = CouponStatus.USED;
            } else if (ZonedDateTime.now().isAfter(issued.getExpiredAt())) {
                effectiveStatus = CouponStatus.EXPIRED;
            } else {
                effectiveStatus = CouponStatus.AVAILABLE;
            }
            return new MyCoupon(issued.getId(), issued.getCouponId(), issued.getExpiredAt(), effectiveStatus);
        }
    }

    public record Issued(
            Long id,
            Long couponId,
            Long userId,
            CouponStatus status,
            ZonedDateTime usedAt,
            ZonedDateTime createdAt
    ) {
        public static Issued from(IssuedCoupon issuedCoupon) {
            return new Issued(issuedCoupon.getId(), issuedCoupon.getCouponId(), issuedCoupon.getUserId(),
                issuedCoupon.getStatus(), issuedCoupon.getUsedAt(), issuedCoupon.getCreatedAt());
        }
    }
}
