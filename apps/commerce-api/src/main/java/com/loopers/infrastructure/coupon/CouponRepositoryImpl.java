package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getId() == null) {
            return couponJpaRepository.save(
                new CouponEntity(coupon.getName(), coupon.getType(), coupon.getValue(),
                    coupon.getMinOrderAmount(), coupon.getExpiredAt())
            ).toDomain();
        }
        CouponEntity entity = couponJpaRepository.findById(coupon.getId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
        entity.updateFrom(coupon);
        return couponJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return couponJpaRepository.findByIdAndDeletedAtIsNull(id).map(CouponEntity::toDomain);
    }

    @Override
    public Page<Coupon> findAll(Pageable pageable) {
        return couponJpaRepository.findAllByDeletedAtIsNull(pageable).map(CouponEntity::toDomain);
    }
}
