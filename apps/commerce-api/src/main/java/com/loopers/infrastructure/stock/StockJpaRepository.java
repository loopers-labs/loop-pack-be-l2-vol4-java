package com.loopers.infrastructure.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockJpaRepository extends JpaRepository<StockEntity, Long> {

    /** 일반 조회 (낙관적 락 경로) — save 시 @Version이 동시성을 보장한다. */
    Optional<StockEntity> findByProductId(Long productId);

    /**
     * 행을 잠그고 조회 (비관적 락, SELECT ... FOR UPDATE).
     * 경합 트랜잭션은 선행 커밋까지 대기한다 → 동시 차감을 직렬화(lost update 방지).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockEntity s where s.productId = :productId")
    Optional<StockEntity> findByProductIdForUpdate(@Param("productId") Long productId);

    /** 여러 상품의 재고 batch 조회 — 상품 목록 inStock 조합 N+1 회피. */
    List<StockEntity> findByProductIdIn(Collection<Long> productIds);
}
