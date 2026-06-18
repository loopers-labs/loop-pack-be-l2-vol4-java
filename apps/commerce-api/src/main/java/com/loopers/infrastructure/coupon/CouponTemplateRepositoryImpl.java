package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Repository
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {
    private final CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Override
    public CouponTemplateModel saveAndFlush(CouponTemplateModel template) {
        return couponTemplateJpaRepository.saveAndFlush(template);
    }

    @Override
    public Optional<CouponTemplateModel> find(UUID id) {
        return couponTemplateJpaRepository.findById(id);
    }

    @Override
    public Optional<CouponTemplateModel> findActive(UUID id) {
        return couponTemplateJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<CouponTemplateModel> findAll(Pageable pageable) {
        return couponTemplateJpaRepository.findAll(pageable);
    }
}
