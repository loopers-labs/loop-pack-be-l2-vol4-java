package com.loopers.coupon.infrastructure;

import com.loopers.coupon.application.UserCouponDisplayStatus;
import com.loopers.coupon.application.UserCouponInfo;
import com.loopers.coupon.application.UserCouponListQuery;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.QUserCoupon;
import com.loopers.coupon.domain.UserCouponStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class UserCouponListQueryDsl implements UserCouponListQuery {

    private static final QUserCoupon userCoupon = QUserCoupon.userCoupon;

    private final JPAQueryFactory queryFactory;

    @Override
    public List<UserCouponInfo> findMyCoupons(Long userId, ZonedDateTime now) {
        return queryFactory
            .select(Projections.constructor(
                UserCouponRow.class,
                userCoupon.id,
                userCoupon.couponTemplateId.value,
                userCoupon.name.value,
                userCoupon.type,
                userCoupon.discountValue.value,
                userCoupon.minimumOrderAmount.value,
                userCoupon.expiration.expiredAt,
                userCoupon.status,
                userCoupon.createdAt,
                userCoupon.usedAt
            ))
            .from(userCoupon)
            .where(
                userCoupon.deletedAt.isNull(),
                userCoupon.owner.userId.eq(userId)
            )
            .orderBy(userCoupon.createdAt.desc(), userCoupon.id.desc())
            .fetch()
            .stream()
            .map(coupon -> coupon.toUserCouponInfo(now))
            .toList();
    }

    public record UserCouponRow(
        Long id,
        Long couponTemplateId,
        String name,
        CouponType type,
        long discountValue,
        Long minimumOrderAmount,
        ZonedDateTime expiredAt,
        UserCouponStatus storedStatus,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt
    ) {

        UserCouponInfo toUserCouponInfo(ZonedDateTime now) {
            return new UserCouponInfo(
                id,
                couponTemplateId,
                name,
                type,
                discountValue,
                minimumOrderAmount,
                expiredAt,
                UserCouponDisplayStatus.fromUserCouponStatus(storedStatus, expiredAt).toDisplayStatus(now),
                issuedAt,
                usedAt
            );
        }
    }
}
