package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<CouponModel, Long> {

    @Query("SELECT c FROM CouponModel c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<CouponModel> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Query("SELECT c FROM CouponModel c WHERE c.deletedAt IS NULL ORDER BY c.createdAt DESC")
    List<CouponModel> findAllByDeletedAtIsNull(Pageable pageable);

    /**
     * clearAutomatically — 벌크 UPDATE 는 영속성 컨텍스트를 거치지 않으므로, 같은 트랜잭션에서
     * 이미 로드된 CouponModel(예: 발급 처리 중 조회한 template)의 issuedCount 가 stale 해지는 것을 막는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponModel c SET c.issuedCount = c.issuedCount + :count "
        + "WHERE c.id = :id AND c.deletedAt IS NULL")
    int increaseIssuedCount(@Param("id") Long id, @Param("count") int count);
}
