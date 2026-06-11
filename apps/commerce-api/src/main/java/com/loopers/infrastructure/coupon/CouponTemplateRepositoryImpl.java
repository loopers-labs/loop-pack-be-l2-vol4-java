package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Override
    public Optional<CouponTemplateModel> findById(Long id) {
        return couponTemplateJpaRepository.findById(id);
    }

    @Override
    public List<CouponTemplateModel> findAllByIds(Set<Long> ids) {
        return couponTemplateJpaRepository.findAllById(ids);
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
