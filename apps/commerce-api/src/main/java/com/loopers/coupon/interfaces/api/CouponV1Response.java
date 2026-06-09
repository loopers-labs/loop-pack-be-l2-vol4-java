package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.application.CouponResult;
import com.loopers.coupon.domain.CouponStatus;
import com.loopers.coupon.domain.CouponType;

import java.time.ZonedDateTime;
import java.util.List;

public class CouponV1Response {

    public record IssueDetail(
            Long id,
            Long couponId,
            CouponType type,
            long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt,
            CouponStatus status
    ) {
        public static IssueDetail from(CouponResult.IssueDetail result) {
            return new IssueDetail(
                    result.id(),
                    result.couponId(),
                    result.type(),
                    result.value(),
                    result.minOrderAmount(),
                    result.expiredAt(),
                    result.status()
            );
        }
    }

    public record MyCoupons(List<Item> coupons) {
        public static MyCoupons from(CouponResult.MyCoupons result) {
            return new MyCoupons(result.coupons().stream().map(Item::from).toList());
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
            public static Item from(CouponResult.MyCoupons.Item item) {
                return new Item(
                        item.id(),
                        item.couponId(),
                        item.type(),
                        item.value(),
                        item.minOrderAmount(),
                        item.expiredAt(),
                        item.status()
                );
            }
        }
    }
}
