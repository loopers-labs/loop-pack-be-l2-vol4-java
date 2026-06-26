package com.loopers.infrastructure.coupon;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<CouponJpaEntity, String> {
    Optional<CouponJpaEntity> findByIdAndDeletedAtIsNull(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponJpaEntity c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<CouponJpaEntity> findByIdWithLock(@Param("id") String id);

    Page<CouponJpaEntity> findAllByUserIdAndDeletedAtIsNull(String userId, Pageable pageable);

    Page<CouponJpaEntity> findAllByCouponTemplateIdAndDeletedAtIsNull(String couponTemplateId, Pageable pageable);

    @Modifying
    @Query("UPDATE CouponJpaEntity c SET c.deletedAt = :now WHERE c.couponTemplateId = :templateId AND c.deletedAt IS NULL")
    void softDeleteAllByTemplateId(@Param("templateId") String templateId, @Param("now") ZonedDateTime now);
}
