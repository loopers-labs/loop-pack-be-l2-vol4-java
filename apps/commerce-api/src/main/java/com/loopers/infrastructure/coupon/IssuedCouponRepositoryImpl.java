package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    public Page<IssuedCouponModel> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable) {
        return issuedCouponJpaRepository.findAllByCouponTemplateId(couponTemplateId, pageable);
    }

    @Override
    public List<IssuedCouponModel> findAllByUserId(Long userId) {
        return issuedCouponJpaRepository.findAllByUserId(userId);
    }

    @Override
    public Optional<IssuedCouponModel> findById(Long id) {
        return issuedCouponJpaRepository.findById(id);
    }

    @Override
    public IssuedCouponModel save(IssuedCouponModel issuedCoupon) {
        try {
            return issuedCouponJpaRepository.save(issuedCoupon);
        } catch (DataIntegrityViolationException e) {
            // (coupon_template_id, user_id) UNIQUE 제약 위반 → 동시 중복 발급 방어
            throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
        }
    }

    @Override
    public boolean existsByCouponTemplateIdAndUserId(Long couponTemplateId, Long userId) {
        return issuedCouponJpaRepository.existsByCouponTemplateIdAndUserId(couponTemplateId, userId);
    }
}
