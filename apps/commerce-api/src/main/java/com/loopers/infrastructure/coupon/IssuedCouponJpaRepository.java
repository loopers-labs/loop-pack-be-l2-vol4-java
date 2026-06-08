package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.model.IssuedCoupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCoupon, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ic FROM IssuedCoupon ic WHERE ic.id = :id AND ic.deletedAt IS NULL")
    Optional<IssuedCoupon> findByIdWithLock(@Param("id") Long id);

    boolean existsByMemberIdAndCouponTemplateId(Long memberId, Long couponTemplateId);

    List<IssuedCoupon> findAllByMemberId(Long memberId);

    Page<IssuedCoupon> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable);
}
