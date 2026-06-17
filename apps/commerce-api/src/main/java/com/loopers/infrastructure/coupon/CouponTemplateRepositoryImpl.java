package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.repository.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository jpaRepository;

    @Override
    public CouponTemplate save(CouponTemplate couponTemplate) {
        return jpaRepository.save(couponTemplate);
    }

    @Override
    public Optional<CouponTemplate> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Page<CouponTemplate> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable);
    }

    @Override
    public void delete(CouponTemplate couponTemplate) {
        couponTemplate.delete();
        jpaRepository.save(couponTemplate);
    }
}
