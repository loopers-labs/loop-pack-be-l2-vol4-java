package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.CouponTemplate;
import com.loopers.coupon.domain.CouponTemplateRepository;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
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
