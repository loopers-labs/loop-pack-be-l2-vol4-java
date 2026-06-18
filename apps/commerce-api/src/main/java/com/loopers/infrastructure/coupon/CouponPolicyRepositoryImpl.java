package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponPolicy;
import com.loopers.domain.coupon.CouponPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CouponPolicyRepositoryImpl implements CouponPolicyRepository {

    private final CouponPolicyJpaRepository couponPolicyJpaRepository;

    @Override
    public CouponPolicy save(CouponPolicy couponPolicy) {
        return couponPolicyJpaRepository.save(couponPolicy);
    }

    @Override
    public Optional<CouponPolicy> findById(Long id) {
        return couponPolicyJpaRepository.findById(id);
    }

    @Override
    public Optional<CouponPolicy> findActiveById(Long id) {
        return couponPolicyJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<CouponPolicy> findAll(Pageable pageable) {
        return couponPolicyJpaRepository.findAll(pageable);
    }

    @Override
    public List<CouponPolicy> findAllByIdIn(Collection<Long> ids) {
        return couponPolicyJpaRepository.findAllByIdIn(ids);
    }
}
