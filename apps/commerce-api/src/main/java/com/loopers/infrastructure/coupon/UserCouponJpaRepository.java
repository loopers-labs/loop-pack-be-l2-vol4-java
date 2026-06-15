package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {

    Optional<UserCoupon> findByUserIdAndTemplateId(Long userId, Long templateId);

    Page<UserCoupon> findAllByUserId(Long userId, Pageable pageable);

    Page<UserCoupon> findAllByTemplateId(Long templateId, Pageable pageable);
}
