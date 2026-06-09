package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {
    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public UserCouponModel save(UserCouponModel userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }

    @Override
    public Optional<UserCouponModel> find(UUID id) {
        return userCouponJpaRepository.findById(id);
    }

    @Override
    public Optional<UserCouponModel> findByIdAndUserId(UUID id, UUID userId) {
        return userCouponJpaRepository.findByIdAndUserId(id, userId);
    }

    @Override
    public boolean existsByUserIdAndTemplateId(UUID userId, UUID templateId) {
        return userCouponJpaRepository.existsByUserIdAndTemplateId(userId, templateId);
    }

    @Override
    public List<UserCouponModel> findByUserId(UUID userId) {
        return userCouponJpaRepository.findByUserId(userId);
    }

    @Override
    public Page<UserCouponModel> findByTemplateId(UUID templateId, Pageable pageable) {
        return userCouponJpaRepository.findByTemplateId(templateId, pageable);
    }

    @Override
    public int useIfAvailable(UUID id, UUID orderId, ZonedDateTime usedAt) {
        return userCouponJpaRepository.useIfAvailable(id, orderId, usedAt);
    }

    @Override
    public int releaseByOrderId(UUID orderId) {
        return userCouponJpaRepository.releaseByOrderId(orderId);
    }

    @Override
    public int releaseByOrderIds(List<UUID> orderIds) {
        return userCouponJpaRepository.releaseByOrderIds(orderIds);
    }
}
