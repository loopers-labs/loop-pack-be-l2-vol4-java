package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }

    @Override
    public Optional<UserCoupon> find(Long id) {
        return userCouponJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<UserCoupon> findWithLock(Long id) {
        return userCouponJpaRepository.findByIdWithLock(id);
    }

    @Override
    public List<UserCoupon> findAllByMemberId(Long memberId) {
        return userCouponJpaRepository.findAllByMemberIdAndDeletedAtIsNull(memberId);
    }

    @Override
    public Page<UserCoupon> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable) {
        return userCouponJpaRepository.findAllByCouponTemplateIdAndDeletedAtIsNull(couponTemplateId, pageable);
    }
}
