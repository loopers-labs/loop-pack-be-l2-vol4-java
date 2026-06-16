package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public Optional<CouponModel> findById(Long id) {
        return couponJpaRepository.findById(id);
    }

    @Override
    public CouponModel save(CouponModel coupon) {
        return couponJpaRepository.save(coupon);
    }

    @Override
    public Page<CouponModel> findAll(Pageable pageable) {
        return couponJpaRepository.findAll(pageable);
    }

    @Override
    public void delete(CouponModel coupon) {
        couponJpaRepository.delete(coupon);
    }
}
