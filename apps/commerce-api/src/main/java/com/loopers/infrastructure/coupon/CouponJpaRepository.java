package com.loopers.infrastructure.coupon;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<CouponJpaEntity, Long> {
    Optional<CouponJpaEntity> findByIdAndDeletedAtIsNull(Long id);
    List<CouponJpaEntity> findAllByDeletedAtIsNull(Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
        update CouponJpaEntity c
           set c.issuedCount = c.issuedCount + 1
         where c.id = :couponId
           and c.deletedAt is null
           and c.issueLimit is not null
           and c.issuedCount < c.issueLimit
        """)
    int increaseIssuedCount(@Param("couponId") Long couponId);
}
