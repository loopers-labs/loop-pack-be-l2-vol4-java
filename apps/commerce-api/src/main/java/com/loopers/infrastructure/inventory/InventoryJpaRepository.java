package com.loopers.infrastructure.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, String> {

    Optional<InventoryJpaEntity> findByProductIdAndDeletedAtIsNull(String productId);

    List<InventoryJpaEntity> findAllByProductIdInAndDeletedAtIsNull(List<String> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryJpaEntity i WHERE i.productId IN :productIds AND i.deletedAt IS NULL ORDER BY i.productId ASC")
    List<InventoryJpaEntity> findAllByProductIdsWithLock(@Param("productIds") List<String> productIds);

    @Modifying
    @Query("UPDATE InventoryJpaEntity i SET i.deletedAt = :now WHERE i.productId = :productId AND i.deletedAt IS NULL")
    void softDeleteByProductId(@Param("productId") String productId, @Param("now") ZonedDateTime now);

    @Modifying
    @Query("UPDATE InventoryJpaEntity i SET i.deletedAt = :now WHERE i.productId IN :productIds AND i.deletedAt IS NULL")
    void softDeleteAllByProductIds(@Param("productIds") List<String> productIds, @Param("now") ZonedDateTime now);
}
