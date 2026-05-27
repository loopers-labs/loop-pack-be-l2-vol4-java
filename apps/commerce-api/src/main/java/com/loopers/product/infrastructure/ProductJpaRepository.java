package com.loopers.product.infrastructure;

import com.loopers.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);
    List<Product> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    List<Product> findAllByDeletedAtIsNullOrderByPriceAsc();

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Product p
        SET p.deletedAt = CURRENT_TIMESTAMP, p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.brandId = :brandId AND p.deletedAt IS NULL
        """)
    int softDeleteByBrandId(@Param("brandId") Long brandId);
}
