package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.MemberCoupon;
import com.loopers.coupon.domain.MemberCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class MemberCouponRepositoryImpl implements MemberCouponRepository {

    private final MemberCouponJpaRepository memberCouponJpaRepository;

    @Override
    public MemberCoupon save(MemberCoupon memberCoupon) {
        return memberCouponJpaRepository.save(memberCoupon);
    }

    @Override
    public Optional<MemberCoupon> find(Long id) {
        return memberCouponJpaRepository.findById(id);
    }

    @Override
    public Optional<MemberCoupon> findByIdForUpdate(Long id) {
        return memberCouponJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public List<MemberCoupon> findByMemberId(Long memberId) {
        return memberCouponJpaRepository.findByMemberId(memberId);
    }

    @Override
    public List<MemberCoupon> findByCouponId(Long couponId) {
        return memberCouponJpaRepository.findByCouponId(couponId);
    }
}
