package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class IssuedCouponRepositoryImpl implements IssuedCouponRepository {

    private final IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Override
    public Page<IssuedCouponModel> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable) {
        return issuedCouponJpaRepository.findAllByCouponTemplateId(couponTemplateId, pageable);
    }

    @Override
    public List<IssuedCouponModel> findAllByUserId(Long userId) {
        return issuedCouponJpaRepository.findAllByUserId(userId);
    }

    @Override
    public Optional<IssuedCouponModel> findById(Long id) {
        return issuedCouponJpaRepository.findById(id);
    }

    @Override
    public IssuedCouponModel save(IssuedCouponModel issuedCoupon) {
        return issuedCouponJpaRepository.save(issuedCoupon);
    }
}
