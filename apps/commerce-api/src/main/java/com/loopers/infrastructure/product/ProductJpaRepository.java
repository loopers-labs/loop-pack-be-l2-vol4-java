package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<ProductModel, UUID> {

    Optional<ProductModel> findByIdAndDeletedAtIsNull(UUID id);

    Page<ProductModel> findAllByDeletedAtIsNull(Pageable pageable);

    @Query("SELECT p FROM ProductModel p WHERE p.brand.id = :brandId AND p.deletedAt IS NULL")
    List<ProductModel> findAllByBrandIdAndDeletedAtIsNull(@Param("brandId") UUID brandId);
}
