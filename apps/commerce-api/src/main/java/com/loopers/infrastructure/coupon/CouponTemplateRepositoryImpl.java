package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
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
    public CouponTemplate save(CouponTemplate couponTemplate) {
        return couponTemplateJpaRepository.save(couponTemplate);
    }

    @Override
    public Optional<CouponTemplate> findActiveById(Long couponTemplateId) {
        return couponTemplateJpaRepository.findByIdAndDeletedAtIsNull(couponTemplateId);
    }

    @Override
    public PageResult<CouponTemplate> findActiveAll(PageQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(), Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
        ));
        return PageResult.from(couponTemplateJpaRepository.findByDeletedAtIsNull(pageable));
    }
}
