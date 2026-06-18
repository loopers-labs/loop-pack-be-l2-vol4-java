package com.loopers.infrastructure.coupon;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponEntity, Long> {

    List<UserCouponEntity> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    List<UserCouponEntity> findByCouponIdOrderByIdDesc(Long couponId, Pageable pageable);

    /**
     * 사용 가능(used_at IS NULL)한 발급분 중 가장 먼저 발급된 것 (낙관적 락 경로).
     * Pageable로 1건 제한 — use()→save 시 @Version이 동시성을 보장한다.
     */
    @Query("select uc from UserCouponEntity uc "
            + "where uc.userId = :userId and uc.couponId = :couponId and uc.usedAt is null "
            + "order by uc.issuedAt asc, uc.id asc")
    List<UserCouponEntity> findAvailable(@Param("userId") Long userId, @Param("couponId") Long couponId, Pageable pageable);

    /**
     * 위와 동일하되 행을 잠그고 조회 (비관적 락, SELECT ... FOR UPDATE).
     * 경합 트랜잭션은 선행 커밋까지 대기한다(UC-20 §5-B).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select uc from UserCouponEntity uc "
            + "where uc.userId = :userId and uc.couponId = :couponId and uc.usedAt is null "
            + "order by uc.issuedAt asc, uc.id asc")
    List<UserCouponEntity> findAvailableForUpdate(@Param("userId") Long userId, @Param("couponId") Long couponId, Pageable pageable);
}
