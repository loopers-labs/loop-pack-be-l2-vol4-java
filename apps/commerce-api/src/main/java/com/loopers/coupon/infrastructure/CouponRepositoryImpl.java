package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public Coupon save(Coupon coupon) {
        return couponJpaRepository.save(coupon);
    }

    @Override
    public Optional<Coupon> find(Long id) {
        return couponJpaRepository.findById(id);
    }

    @Override
    public List<Coupon> findAll() {
        return couponJpaRepository.findAll();
    }

    @Override
    public List<Coupon> findAllByIds(Collection<Long> ids) {
        return couponJpaRepository.findAllById(ids);
    }
}
