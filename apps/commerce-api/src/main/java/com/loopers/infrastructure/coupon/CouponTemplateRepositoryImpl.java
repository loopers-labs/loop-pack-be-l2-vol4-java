package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Override
    public CouponTemplateModel save(CouponTemplateModel template) {
        return couponTemplateJpaRepository.save(template);
    }

    @Override
    public Optional<CouponTemplateModel> findById(Long id) {
        return couponTemplateJpaRepository.findById(id);
    }

    @Override
    public Page<CouponTemplateModel> findAll(PageRequest pageRequest) {
        return couponTemplateJpaRepository.findAll(pageRequest);
    }
}
