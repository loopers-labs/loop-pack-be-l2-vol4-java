package com.loopers.infrastructure.coupon;

import com.loopers.application.coupon.UserCouponDisplayStatus;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.application.coupon.UserCouponListQuery;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.QCouponTemplate;
import com.loopers.domain.coupon.QUserCoupon;
import com.loopers.domain.coupon.UserCouponStatus;
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
    private static final QCouponTemplate couponTemplate = QCouponTemplate.couponTemplate;

    private final JPAQueryFactory queryFactory;

    @Override
    public List<UserCouponInfo> findMyCoupons(Long userId, ZonedDateTime now) {
        return queryFactory
            .select(Projections.constructor(
                UserCouponRow.class,
                userCoupon.id,
                couponTemplate.id,
                couponTemplate.name.value,
                couponTemplate.type,
                couponTemplate.discountValue.value,
                couponTemplate.minimumOrderAmount.value,
                couponTemplate.expiration.expiredAt,
                userCoupon.status,
                userCoupon.createdAt,
                userCoupon.usedAt
            ))
            .from(userCoupon)
            .join(couponTemplate).on(couponTemplate.id.eq(userCoupon.couponTemplateId.value))
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
