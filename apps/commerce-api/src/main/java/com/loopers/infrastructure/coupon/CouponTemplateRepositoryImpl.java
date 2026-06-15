package com.loopers.infrastructure.coupon;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Override
    public Optional<CouponTemplate> find(Long id) {
        return couponTemplateJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public PageResult<CouponTemplate> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("id")));
        Page<CouponTemplate> result = couponTemplateJpaRepository.findAllByDeletedAtIsNull(pageable);
        return new PageResult<>(
                result.getContent(),
                page,
                size,
                result.hasNext(),
                result.getTotalElements()
        );
    }

    @Override
    public CouponTemplate save(CouponTemplate template) {
        return couponTemplateJpaRepository.save(template);
    }
}
