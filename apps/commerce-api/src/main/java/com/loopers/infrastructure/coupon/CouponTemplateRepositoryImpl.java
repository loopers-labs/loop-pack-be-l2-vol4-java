package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
    public Optional<CouponTemplate> find(Long id) {
        return couponTemplateJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<CouponTemplate> findAll() {
        return couponTemplateJpaRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public void delete(Long id) {
        couponTemplateJpaRepository.findByIdAndDeletedAtIsNull(id)
                .ifPresent(t -> {
                    t.delete();
                    couponTemplateJpaRepository.save(t);
                });
    }
}
