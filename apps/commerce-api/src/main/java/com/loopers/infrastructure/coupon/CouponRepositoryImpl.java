package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public CouponModel save(CouponModel coupon) {
        return couponJpaRepository.save(coupon);
    }

    @Override
    public Optional<CouponModel> findById(Long id) {
        return couponJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<CouponModel> findAll(int page, int size) {
        return couponJpaRepository.findAllByDeletedAtIsNull(PageRequest.of(page, size));
    }

    @Override
    public List<CouponModel> findAllByIds(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return couponJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public void delete(CouponModel coupon) {
        coupon.delete();   // soft delete
        couponJpaRepository.save(coupon);
    }
}
