package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.StockModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockJpaRepository extends JpaRepository<StockModel, Long> {
    Optional<StockModel> findByProductId(Long productId);
    List<StockModel> findAllByProductIdIn(List<Long> productIds);

    /**
     * 비관적 쓰기 락(SELECT ... FOR UPDATE)으로 재고를 조회한다.
     *
     * <p>주문 시 동시 재고 차감의 Lost Update 를 방지한다. 락을 먼저 잡은 트랜잭션이
     * 끝날 때까지 다른 트랜잭션은 대기하므로, 동일 상품 동시 주문이 순차 처리되어 재고 음수를 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockModel s WHERE s.productId = :productId")
    Optional<StockModel> findByProductIdForUpdate(@Param("productId") Long productId);
}
