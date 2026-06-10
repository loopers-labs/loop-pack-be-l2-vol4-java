package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.model.IssuedCoupon;
import com.loopers.domain.coupon.repository.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class IssuedCouponRepositoryImpl implements IssuedCouponRepository {

    private final IssuedCouponJpaRepository jpaRepository;

    @Override
    public IssuedCoupon save(IssuedCoupon issuedCoupon) {
        return jpaRepository.save(issuedCoupon);
    }

    @Override
    public Optional<IssuedCoupon> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public boolean existsByMemberIdAndCouponTemplateId(Long memberId, Long couponTemplateId) {
        return jpaRepository.existsByMemberIdAndCouponTemplateId(memberId, couponTemplateId);
    }

    @Override
    public List<IssuedCoupon> findAllByMemberId(Long memberId) {
        return jpaRepository.findAllByMemberId(memberId);
    }

    @Override
    public Page<IssuedCoupon> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable) {
        return jpaRepository.findAllByCouponTemplateId(couponTemplateId, pageable);
    }
}
