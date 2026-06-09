package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, UUID> {
    Optional<UserCouponModel> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndTemplateId(UUID userId, UUID templateId);

    List<UserCouponModel> findByUserId(UUID userId);

    Page<UserCouponModel> findByTemplateId(UUID templateId, Pageable pageable);
}
