package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponStatus;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.UserCoupon;

import java.time.ZonedDateTime;
import java.util.List;

public class CouponResult {

    public record Detail(
            Long id,
            String name,
            CouponType type,
            long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt
    ) {
        public static Detail from(Coupon coupon) {
            return new Detail(
                    coupon.getId(),
                    coupon.getName(),
                    coupon.getType(),
                    coupon.getValue(),
                    coupon.getMinOrderAmount(),
                    coupon.getExpiredAt()
            );
        }
    }

    public record IssueDetail(
            Long id,
            Long couponId,
            Long userId,
            CouponType type,
            long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt,
            CouponStatus status
    ) {
        public static IssueDetail from(UserCoupon userCoupon, ZonedDateTime now) {
            return new IssueDetail(
                    userCoupon.getId(),
                    userCoupon.getCouponId(),
                    userCoupon.getUserId(),
                    userCoupon.getType(),
                    userCoupon.getValue(),
                    userCoupon.getMinOrderAmount(),
                    userCoupon.getExpiredAt(),
                    userCoupon.displayStatus(now)
            );
        }
    }

    public record MyCoupons(List<Item> coupons) {
        public static MyCoupons from(List<UserCoupon> userCoupons, ZonedDateTime now) {
            return new MyCoupons(userCoupons.stream().map(uc -> Item.from(uc, now)).toList());
        }

        public record Item(
                Long id,
                Long couponId,
                CouponType type,
                long value,
                Long minOrderAmount,
                ZonedDateTime expiredAt,
                CouponStatus status
        ) {
            public static Item from(UserCoupon userCoupon, ZonedDateTime now) {
                return new Item(
                        userCoupon.getId(),
                        userCoupon.getCouponId(),
                        userCoupon.getType(),
                        userCoupon.getValue(),
                        userCoupon.getMinOrderAmount(),
                        userCoupon.getExpiredAt(),
                        userCoupon.displayStatus(now)
                );
            }
        }
    }
}
