package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
        IssuedCouponJpaEntity issuedCouponJpaEntity = issuedCoupon.getId() == null
            ? IssuedCouponJpaEntity.from(issuedCoupon)
            : issuedCouponJpaRepository.findById(issuedCoupon.getId())
                .map(existingIssuedCoupon -> {
                    existingIssuedCoupon.update(issuedCoupon);
                    return existingIssuedCoupon;
                })
                .orElseGet(() -> IssuedCouponJpaEntity.from(issuedCoupon));

        try {
            return issuedCouponJpaRepository.saveAndFlush(issuedCouponJpaEntity).toDomain();
        } catch (DataIntegrityViolationException exception) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
        }
    }

    @Override
    public Optional<IssuedCoupon> find(Long id) {
        return issuedCouponJpaRepository.findByIdAndDeletedAtIsNull(id)
            .map(IssuedCouponJpaEntity::toDomain);
    }

    @Override
    public Optional<IssuedCoupon> findForUpdate(Long id) {
        return issuedCouponJpaRepository.findWithLockByIdAndDeletedAtIsNull(id)
            .map(IssuedCouponJpaEntity::toDomain);
    }

    @Override
    public Optional<IssuedCoupon> findByCouponIdAndUserLoginId(Long couponId, String userLoginId) {
        return issuedCouponJpaRepository.findByCouponIdAndUserLoginIdAndDeletedAtIsNull(couponId, userLoginId)
            .map(IssuedCouponJpaEntity::toDomain);
    }

    @Override
    public Optional<IssuedCoupon> findByCouponIdAndUserLoginIdForUpdate(Long couponId, String userLoginId) {
        return issuedCouponJpaRepository.findWithLockByCouponIdAndUserLoginIdAndDeletedAtIsNull(couponId, userLoginId)
            .map(IssuedCouponJpaEntity::toDomain);
    }

    @Override
    public List<IssuedCoupon> findAllByUserLoginId(String userLoginId) {
        return issuedCouponJpaRepository.findAllByUserLoginIdAndDeletedAtIsNull(userLoginId).stream()
            .map(IssuedCouponJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<IssuedCoupon> findAllByCouponId(Long couponId, int page, int size) {
        return issuedCouponJpaRepository.findAllByCouponIdAndDeletedAtIsNull(couponId, PageRequest.of(page, size)).stream()
            .map(IssuedCouponJpaEntity::toDomain)
            .toList();
    }
}
