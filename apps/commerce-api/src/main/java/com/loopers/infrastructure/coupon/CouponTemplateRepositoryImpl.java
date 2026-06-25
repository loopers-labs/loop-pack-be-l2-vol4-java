package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateEntity;
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
    public CouponTemplateEntity save(CouponTemplateEntity template) {
        return CouponTemplateMapper.toDomain(
                couponTemplateJpaRepository.save(CouponTemplateMapper.toJpaEntity(template)));
    }

    @Override
    public Optional<CouponTemplateEntity> findById(String id) {
        return couponTemplateJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(CouponTemplateMapper::toDomain);
    }

    @Override
    public Page<CouponTemplateEntity> findAll(Pageable pageable) {
        return couponTemplateJpaRepository.findAllByDeletedAtIsNull(pageable)
                .map(CouponTemplateMapper::toDomain);
    }
}
