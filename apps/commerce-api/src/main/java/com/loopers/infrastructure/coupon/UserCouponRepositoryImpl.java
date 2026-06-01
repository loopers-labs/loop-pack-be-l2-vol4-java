package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public boolean issueOnce(UserCoupon userCoupon) {
        int issuedCount = userCouponJpaRepository.issueOnce(
            userCoupon.getUserId(),
            userCoupon.getCouponTemplateId(),
            userCoupon.getStatus().name()
        );
        return issuedCount == 1;
    }

    @Override
    public Optional<UserCoupon> findIssuedCoupon(Long userId, Long couponTemplateId) {
        return userCouponJpaRepository.findByOwnerUserIdAndCouponTemplateIdValue(userId, couponTemplateId);
    }
}
