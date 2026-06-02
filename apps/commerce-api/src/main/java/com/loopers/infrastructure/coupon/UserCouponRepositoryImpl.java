package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.DuplicateCouponIssueException;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

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
            throw new DuplicateCouponIssueException(e);
        }
    }

    @Override
    public Optional<UserCoupon> findIssuedCoupon(Long userId, Long couponTemplateId) {
        return userCouponJpaRepository.findByOwnerUserIdAndCouponTemplateIdValue(userId, couponTemplateId);
    }
}
