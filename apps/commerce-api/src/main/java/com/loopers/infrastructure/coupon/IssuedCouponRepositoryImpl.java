package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class IssuedCouponRepositoryImpl implements IssuedCouponRepository {

    private final IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Override
    public IssuedCoupon save(IssuedCoupon issuedCoupon) {
        IssuedCouponJpaEntity entity = issuedCoupon.isNew()
            ? IssuedCouponJpaEntity.from(issuedCoupon)
            : issuedCouponJpaRepository.findById(issuedCoupon.getId())
                .orElseGet(() -> IssuedCouponJpaEntity.from(issuedCoupon));
        entity.apply(issuedCoupon);
        return issuedCouponJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<IssuedCoupon> findForUpdate(Long issuedCouponId) {
        return issuedCouponJpaRepository.findWithLockById(issuedCouponId).map(IssuedCouponJpaEntity::toDomain);
    }

    @Override
    public List<IssuedCoupon> findByUserId(String userId, int page, int size) {
        return issuedCouponJpaRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
            .stream()
            .map(IssuedCouponJpaEntity::toDomain)
            .toList();
    }

    @Override
    public long countByUserId(String userId) {
        return issuedCouponJpaRepository.countByUserId(userId);
    }

    @Override
    public List<IssuedCoupon> findByCouponTemplateId(Long couponTemplateId, int page, int size) {
        return issuedCouponJpaRepository.findByCouponTemplateIdOrderByCreatedAtDesc(
                couponTemplateId,
                PageRequest.of(page, size)
            )
            .stream()
            .map(IssuedCouponJpaEntity::toDomain)
            .toList();
    }

    @Override
    public long countByCouponTemplateId(Long couponTemplateId) {
        return issuedCouponJpaRepository.countByCouponTemplateId(couponTemplateId);
    }

    @Override
    public long countByCouponTemplateIdAndUserId(Long couponTemplateId, String userId) {
        return issuedCouponJpaRepository.countByCouponTemplateIdAndUserId(couponTemplateId, userId);
    }
}
