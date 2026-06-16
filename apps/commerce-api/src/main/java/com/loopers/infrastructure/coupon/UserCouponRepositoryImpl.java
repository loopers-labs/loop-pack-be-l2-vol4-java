package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.*;
import com.loopers.domain.coupon.enums.UserCouponStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public UserCouponModel save(UserCouponModel userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }

    @Override
    public Optional<UserCouponModel> findById(Long id) {
        return userCouponJpaRepository.findById(id);
    }

    @Override
    public List<UserCouponModel> findAllByUserId(Long userId) {
        QUserCouponModel userCoupon = QUserCouponModel.userCouponModel;
        QCouponModel coupon = QCouponModel.couponModel;

        return queryFactory
                .selectFrom(userCoupon)
                .join(userCoupon.coupon, coupon).fetchJoin()
                .where(userCoupon.userId.eq(userId))
                .fetch();
    }

    @Override
    public boolean useIfIssued(Long id, ZonedDateTime usedAt) {
        QUserCouponModel userCoupon = QUserCouponModel.userCouponModel;
        return queryFactory
                .update(userCoupon)
                .set(userCoupon.status, UserCouponStatus.USED)
                .set(userCoupon.usedAt, usedAt)
                .where(userCoupon.id.eq(id).and(userCoupon.status.eq(UserCouponStatus.ISSUED)))
                .execute() > 0;
    }

    @Override
    public Page<UserCouponIssue> findAllByCouponId(Long couponId, Pageable pageable) {
        QUserCouponModel userCoupon = QUserCouponModel.userCouponModel;

        List<UserCouponIssue> content = queryFactory
                .select(Projections.constructor(UserCouponIssue.class,
                        userCoupon.id,
                        userCoupon.userId,
                        userCoupon.coupon.id,
                        userCoupon.status,
                        userCoupon.usedAt
                ))
                .from(userCoupon)
                .where(userCoupon.coupon.id.eq(couponId))
                .orderBy(userCoupon.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return PageableExecutionUtils.getPage(content, pageable, () ->
                queryFactory
                        .select(userCoupon.count())
                        .from(userCoupon)
                        .where(userCoupon.coupon.id.eq(couponId))
                        .fetchOne());
    }
}
