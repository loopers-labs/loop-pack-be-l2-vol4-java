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
        return jpaRepository.findByUserIdOrderByIssuedAtDesc(userId);
    }

    @Override
    public List<UserCoupon> findAllByCouponId(Long couponId, int page, int size) {
        return jpaRepository.findByCouponId(
            couponId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt"))
        ).getContent();
    }
}
