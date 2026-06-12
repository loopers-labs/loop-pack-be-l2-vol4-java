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
        CouponJpaEntity couponJpaEntity = coupon.getId() == null
            ? CouponJpaEntity.from(coupon)
            : couponJpaRepository.findById(coupon.getId())
                .map(existingCoupon -> {
                    existingCoupon.update(coupon);
                    return existingCoupon;
                })
                .orElseGet(() -> CouponJpaEntity.from(coupon));

        return couponJpaRepository.save(couponJpaEntity).toDomain();
    }

    @Override
    public Optional<Coupon> find(Long id) {
        return couponJpaRepository.findByIdAndDeletedAtIsNull(id)
            .map(CouponJpaEntity::toDomain);
    }

    @Override
    public List<Coupon> findAll(int page, int size) {
        return couponJpaRepository.findAllByDeletedAtIsNull(PageRequest.of(page, size)).stream()
            .map(CouponJpaEntity::toDomain)
            .toList();
    }
}
