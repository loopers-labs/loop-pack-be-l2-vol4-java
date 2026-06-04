package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        try {
            return userCouponJpaRepository.saveAndFlush(userCoupon);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
        }
    }

    @Override
    public Optional<UserCoupon> findById(Long userCouponId) {
        return userCouponJpaRepository.findById(userCouponId);
    }

    @Override
    public Optional<UserCoupon> findIssuedCoupon(Long userId, Long couponTemplateId) {
        return userCouponJpaRepository.findByOwnerUserIdAndCouponTemplateIdValue(userId, couponTemplateId);
    }

    @Override
    public boolean useAvailableCoupon(Long userCouponId, Long userId, ZonedDateTime usedAt) {
        int updatedCount = userCouponJpaRepository.useAvailableCoupon(
            userCouponId,
            userId,
            usedAt,
            UserCouponStatus.AVAILABLE,
            UserCouponStatus.USED
        );
        return updatedCount == 1;
    }
}
