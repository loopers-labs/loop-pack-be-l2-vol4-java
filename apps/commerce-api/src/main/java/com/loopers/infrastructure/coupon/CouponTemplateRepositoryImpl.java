package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Override
    public CouponTemplate save(CouponTemplate couponTemplate) {
        CouponTemplateJpaEntity entity = couponTemplate.isNew()
            ? CouponTemplateJpaEntity.from(couponTemplate)
            : couponTemplateJpaRepository.findById(couponTemplate.getId())
                .orElseGet(() -> CouponTemplateJpaEntity.from(couponTemplate));
        entity.apply(couponTemplate);
        return couponTemplateJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<CouponTemplate> find(Long couponTemplateId) {
        return couponTemplateJpaRepository.findById(couponTemplateId).map(CouponTemplateJpaEntity::toDomain);
    }

    @Override
    public Optional<CouponTemplate> findActiveForUpdate(Long couponTemplateId) {
        return couponTemplateJpaRepository.findWithLockByIdAndDeletedAtIsNull(couponTemplateId)
            .map(CouponTemplateJpaEntity::toDomain);
    }

    @Override
    public List<CouponTemplate> findAll(int page, int size) {
        return couponTemplateJpaRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
            .stream()
            .map(CouponTemplateJpaEntity::toDomain)
            .toList();
    }

    @Override
    public long countAll() {
        return couponTemplateJpaRepository.count();
    }
}
