package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Override
    public Optional<CouponTemplateModel> findById(Long id) {
        return couponTemplateJpaRepository.findById(id);
    }

    @Override
    public Page<CouponTemplateModel> findAll(Pageable pageable) {
        return couponTemplateJpaRepository.findAll(pageable);
    }

    @Override
    public CouponTemplateModel save(CouponTemplateModel couponTemplate) {
        return couponTemplateJpaRepository.save(couponTemplate);
    }
}
