package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

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
    public List<Coupon> findAll(int page, int size) {
        return couponJpaRepository.findAll(PageRequest.of(page, size)).getContent();
    }

    @Override
    public boolean existsById(Long id) {
        return id != null && couponJpaRepository.existsById(id);
    }

    @Override
    public void delete(Long id) {
        couponJpaRepository.deleteById(id);
    }
}
