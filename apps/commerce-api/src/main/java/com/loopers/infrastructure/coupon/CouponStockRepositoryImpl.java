package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponStockModel;
import com.loopers.domain.coupon.CouponStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponStockRepositoryImpl implements CouponStockRepository {

    private final CouponStockJpaRepository couponStockJpaRepository;

    @Override
    public CouponStockModel save(CouponStockModel couponStock) {
        return couponStockJpaRepository.save(couponStock);
    }

    @Override
    public Optional<CouponStockModel> findByCouponId(Long couponId) {
        return couponStockJpaRepository.findByCouponIdAndDeletedAtIsNull(couponId);
    }
}
