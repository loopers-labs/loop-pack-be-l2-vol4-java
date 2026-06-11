package com.loopers.infrastructure.inventory;

import com.loopers.domain.inventory.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryJpaRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndDeletedAtIsNull(Long productId);

    List<Inventory> findAllByProductIdInAndDeletedAtIsNull(Collection<Long> productIds);

    // 비관락(FOR UPDATE) — productId 오름차순으로 락을 획득해 교착(데드락) 가능성을 줄인다.
    // deleted_at IS NULL 필터로, 삭제(소프트) 커밋이 먼저면 그 행은 잠기지 않고 빠져 주문이 거부된다
    // (삭제↔주문 경쟁 차단 — 삭제는 같은 행에 UPDATE 락을 잡아 직렬화된다).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Inventory> findAllByProductIdInAndDeletedAtIsNullOrderByProductIdAsc(Collection<Long> productIds);

    // 상품 일괄 삭제(브랜드 삭제 등)의 재고 cascade — productId 로만 참조(ID 참조).
    // 벌크 UPDATE 는 @PreUpdate 를 타지 않으므로 updated_at 을 직접 세팅한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Inventory i
        SET i.deletedAt = CURRENT_TIMESTAMP,
            i.updatedAt = CURRENT_TIMESTAMP
        WHERE i.productId IN :productIds AND i.deletedAt IS NULL
    """)
    int bulkSoftDeleteByProductIds(@Param("productIds") Collection<Long> productIds);
}
