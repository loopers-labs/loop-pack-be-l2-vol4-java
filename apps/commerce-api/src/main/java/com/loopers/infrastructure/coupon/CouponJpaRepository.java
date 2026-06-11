package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<CouponModel, Long> {

    Optional<CouponModel> findByIdAndDeletedAtIsNull(Long id);

    Page<CouponModel> findAllByDeletedAtIsNull(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponModel c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<CouponModel> findByIdForUpdate(@Param("id") Long id);
}