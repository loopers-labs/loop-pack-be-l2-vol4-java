package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, Long> {
    List<UserCouponModel> findByUserId(Long userId);                       // 메서드명으로 쿼리 자동 생성
    List<UserCouponModel> findByCouponId(Long couponId, Pageable pageable); // 발급 내역 (페이징)
}
