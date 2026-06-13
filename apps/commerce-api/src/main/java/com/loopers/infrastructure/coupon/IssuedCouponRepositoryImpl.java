package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
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
public class IssuedCouponRepositoryImpl implements IssuedCouponRepository {

    private final IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Override
    public IssuedCoupon save(IssuedCoupon issuedCoupon) {
        if (issuedCoupon.getId() == null) {
            return issuedCouponJpaRepository.saveAndFlush(
                new IssuedCouponEntity(issuedCoupon.getCouponId(), issuedCoupon.getUserId(), issuedCoupon.getExpiredAt())
            ).toDomain();
        }
        IssuedCouponEntity entity = issuedCouponJpaRepository.findById(issuedCoupon.getId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 발급 쿠폰입니다."));
        entity.updateFrom(issuedCoupon);
        return issuedCouponJpaRepository.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<IssuedCoupon> findById(Long id) {
        return issuedCouponJpaRepository.findById(id).map(IssuedCouponEntity::toDomain);
    }

    @Override
    public List<IssuedCoupon> findAllByUserId(Long userId) {
        return issuedCouponJpaRepository.findAllByUserId(userId).stream()
            .map(IssuedCouponEntity::toDomain)
            .toList();
    }

    @Override
    public Page<IssuedCoupon> findAllByCouponId(Long couponId, Pageable pageable) {
        return issuedCouponJpaRepository.findAllByCouponId(couponId, pageable)
            .map(IssuedCouponEntity::toDomain);
    }
}
