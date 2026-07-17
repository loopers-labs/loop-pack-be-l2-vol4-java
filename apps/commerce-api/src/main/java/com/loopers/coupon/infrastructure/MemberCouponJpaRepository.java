package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.MemberCoupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberCouponJpaRepository extends JpaRepository<MemberCoupon, Long> {

    List<MemberCoupon> findByMemberId(Long memberId);

    List<MemberCoupon> findByCouponId(Long couponId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mc from MemberCoupon mc where mc.id = :id")
    Optional<MemberCoupon> findByIdForUpdate(@Param("id") Long id);
}
