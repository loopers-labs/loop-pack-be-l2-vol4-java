package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Override
    public CouponTemplate save(CouponTemplate template) {
        return couponTemplateJpaRepository.save(template);
    }

    @Override
    public Optional<CouponTemplate> findById(Long id) {
        return couponTemplateJpaRepository.findById(id);
    }

    @Override
    public List<CouponTemplate> findAllByIds(Collection<Long> ids) {
        return couponTemplateJpaRepository.findAllById(ids);
    }

    @Override
    public Page<CouponTemplate> findAll(Pageable pageable) {
        return couponTemplateJpaRepository.findAll(pageable);
    }

    @Override
    public void deleteById(Long id) {
        couponTemplateJpaRepository.deleteById(id);
    }
}
