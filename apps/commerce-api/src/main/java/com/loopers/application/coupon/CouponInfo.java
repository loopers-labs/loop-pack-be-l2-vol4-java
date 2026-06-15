package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCoupon;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class CouponInfo {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private CouponInfo() {
    }

    /** 쿠폰 템플릿(어드민 등록/수정/조회) */
    public record Template(
            Long id,
            String name,
            DiscountType discountType,
            long discountValue,
            long minOrderAmount,
            int validDays
    ) {

        public static Template from(CouponTemplate template) {
            return new Template(
                    template.getId(),
                    template.getName(),
                    template.getDiscountPolicy().getType(),
                    template.getDiscountPolicy().getValue(),
                    template.getDiscountPolicy().getMinOrderAmount(),
                    template.getValidDays()
            );
        }
    }

    /** 발급 결과(대고객 발급 응답) */
    public record Issued(
            Long id,
            Long templateId,
            String couponName,
            DiscountType discountType,
            long discountValue,
            long minOrderAmount,
            CouponStatus status,
            LocalDateTime expiresAt
    ) {

        public static Issued from(UserCoupon coupon) {
            return new Issued(
                    coupon.getId(),
                    coupon.getTemplateId(),
                    coupon.getCouponName(),
                    coupon.getDiscountPolicy().getType(),
                    coupon.getDiscountPolicy().getValue(),
                    coupon.getDiscountPolicy().getMinOrderAmount(),
                    coupon.getStatus(),
                    toLocal(coupon.getExpiresAt())
            );
        }
    }

    /** 내 쿠폰 목록 항목(대고객) — 상태는 조회 시점 기준 노출값 */
    public record MyCoupon(
            Long id,
            String couponName,
            DiscountType discountType,
            long discountValue,
            long minOrderAmount,
            CouponStatus status,
            LocalDateTime expiresAt
    ) {

        public static MyCoupon from(UserCoupon coupon, ZonedDateTime now) {
            return new MyCoupon(
                    coupon.getId(),
                    coupon.getCouponName(),
                    coupon.getDiscountPolicy().getType(),
                    coupon.getDiscountPolicy().getValue(),
                    coupon.getDiscountPolicy().getMinOrderAmount(),
                    coupon.displayStatus(now),
                    toLocal(coupon.getExpiresAt())
            );
        }
    }

    /** 발급 내역 항목(어드민) — 발급자·상태·만료일 */
    public record IssueHistoryItem(
            Long id,
            Long userId,
            CouponStatus status,
            LocalDateTime expiresAt,
            LocalDateTime usedAt
    ) {

        public static IssueHistoryItem from(UserCoupon coupon, ZonedDateTime now) {
            return new IssueHistoryItem(
                    coupon.getId(),
                    coupon.getUserId(),
                    coupon.displayStatus(now),
                    toLocal(coupon.getExpiresAt()),
                    toLocal(coupon.getUsedAt())
            );
        }
    }

    private static LocalDateTime toLocal(ZonedDateTime time) {
        return time == null ? null : time.withZoneSameInstant(ZONE).toLocalDateTime();
    }
}
