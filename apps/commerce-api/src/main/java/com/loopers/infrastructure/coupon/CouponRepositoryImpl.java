package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponEntity;
import com.loopers.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public CouponEntity save(CouponEntity coupon) {
        return CouponMapper.toDomain(couponJpaRepository.save(CouponMapper.toJpaEntity(coupon)));
    }

    @Override
    public Optional<CouponEntity> findById(String id) {
        return couponJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(CouponMapper::toDomain);
    }

    @Override
    public Optional<CouponEntity> findByIdWithLock(String id) {
        return couponJpaRepository.findByIdWithLock(id)
                .map(CouponMapper::toDomain);
    }

    @Override
    public Page<CouponEntity> findAllByUserId(String userId, Pageable pageable) {
        return couponJpaRepository.findAllByUserIdAndDeletedAtIsNull(userId, pageable)
                .map(CouponMapper::toDomain);
    }

    @Override
    public Page<CouponEntity> findAllByCouponTemplateId(String couponTemplateId, Pageable pageable) {
        return couponJpaRepository.findAllByCouponTemplateIdAndDeletedAtIsNull(couponTemplateId, pageable)
                .map(CouponMapper::toDomain);
    }

    @Override
    public void softDeleteAllByTemplateId(String couponTemplateId) {
        couponJpaRepository.softDeleteAllByTemplateId(couponTemplateId, ZonedDateTime.now());
    }
}
