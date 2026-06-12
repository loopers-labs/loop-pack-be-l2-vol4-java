package com.loopers.tddstudy.infrastructure.coupon;

import com.loopers.tddstudy.domain.coupon.Coupon;
import com.loopers.tddstudy.domain.coupon.CouponRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    public CouponRepositoryImpl(CouponJpaRepository couponJpaRepository) {
        this.couponJpaRepository = couponJpaRepository;
    }
    @Override
    public Coupon save(Coupon coupon) {
        return couponJpaRepository.save(coupon);
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return couponJpaRepository.findById(id);
    }

    @Override
    public List<Coupon> findAll() {
        return couponJpaRepository.findAll();
    }

    @Override
    public void deleteById(Long id) {
        couponJpaRepository.deleteById(id);
    }

}
