package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.application.CouponIssueRequestResult;
import com.loopers.coupon.application.CouponResult;
import com.loopers.coupon.domain.CouponStatus;
import com.loopers.coupon.domain.CouponType;

import java.time.ZonedDateTime;
import java.util.List;

public class CouponV1Response {

    /** 선착순 발급 요청 접수 응답. 실제 발급 결과는 requestId 로 polling 한다. */
    public record IssueRequestAccepted(String requestId) {
    }

    /** 선착순 발급 요청 결과 조회 응답. status: PENDING|SUCCESS|REJECTED */
    public record IssueRequestResult(String requestId, String status, String reason) {
        public static IssueRequestResult from(CouponIssueRequestResult result) {
            return new IssueRequestResult(result.requestId(), result.status().name(), result.reason());
        }
    }

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
