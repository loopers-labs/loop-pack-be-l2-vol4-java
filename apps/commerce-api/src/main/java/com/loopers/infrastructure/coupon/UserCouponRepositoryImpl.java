package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository jpaRepository;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        return jpaRepository.save(userCoupon);
    }

    @Override
    public Optional<UserCoupon> find(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<UserCoupon> findAllByUserId(Long userId) {
        return jpaRepository.findAllByUserId(userId);
    }

    @Override
    public List<UserCoupon> findAllByCouponTemplateId(Long couponTemplateId, int page, int size) {
        return jpaRepository.findAllByCouponTemplateId(
            couponTemplateId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt"))
        );
    }

    @Override
    public Optional<UserCoupon> findForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id);
    }

    @Override
    public boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId) {
        return jpaRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId);
    }
}
