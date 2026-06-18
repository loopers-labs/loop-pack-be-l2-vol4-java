package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public UserCouponModel save(UserCouponModel userCoupon) {
        if (userCoupon.getId() == null) {
            return userCouponJpaRepository.save(UserCouponEntity.from(userCoupon)).toDomain();
        }
        UserCouponEntity entity = userCouponJpaRepository.findById(userCoupon.getId())
            .orElseThrow(() -> new IllegalStateException("UserCouponEntity not found: " + userCoupon.getId()));
        entity.applyUpdate(userCoupon.getStatus(), userCoupon.getVersion());
        return userCouponJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<UserCouponModel> findById(Long id) {
        return userCouponJpaRepository.findById(id).map(UserCouponEntity::toDomain);
    }

    @Override
    public List<UserCouponModel> findAllByUserId(Long userId) {
        return userCouponJpaRepository.findAllByUserId(userId).stream()
            .map(UserCouponEntity::toDomain)
            .toList();
    }

    @Override
    public Page<UserCouponModel> findAllByCouponId(Long couponId, Pageable pageable) {
        return userCouponJpaRepository.findAllByCouponId(couponId, pageable).map(UserCouponEntity::toDomain);
    }

    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId);
    }
}
