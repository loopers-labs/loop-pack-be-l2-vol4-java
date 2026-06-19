package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, Long> {

    /** 쿠폰 정보를 함께 로드 — computedStatus() 호출 시 LazyInit 방지 */
    @Query("SELECT uc FROM UserCouponModel uc JOIN FETCH uc.coupon WHERE uc.id = :id")
    Optional<UserCouponModel> findByIdWithCoupon(@Param("id") Long id);

    @EntityGraph(attributePaths = {"coupon"})
    Page<UserCouponModel> findAllByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"coupon"})
    Page<UserCouponModel> findAllByCouponId(Long couponId, Pageable pageable);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
}
