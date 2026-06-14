package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponRepositoryImpl implements CouponRepository {
    private final CouponJpaRepository couponJpaRepository;

    @Override
    public Coupon save(Coupon coupon) {
        return couponJpaRepository.save(coupon);
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return couponJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<Coupon> findAllById(List<Long> ids) {
        return couponJpaRepository.findAllById(ids);
    }

    @Override
    public List<Coupon> findAll(int page, int size) {
        return couponJpaRepository
            .findByDeletedAtIsNull(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
            .getContent();
    }

    @Override
    public long count() {
        return couponJpaRepository.countByDeletedAtIsNull();
    }
}
