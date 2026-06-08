package com.loopers.product.infrastructure;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long>, ProductJpaRepositoryCustom {
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);
    Optional<Product> findByIdAndStatusAndDeletedAtIsNull(Long id, ProductStatus status);
    boolean existsByIdAndStatusAndDeletedAtIsNull(Long id, ProductStatus status);
    List<Product> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Product p
        SET p.deletedAt = CURRENT_TIMESTAMP, p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.brandId = :brandId AND p.deletedAt IS NULL
        """)
    int softDeleteByBrandId(@Param("brandId") Long brandId);
}
