package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);
    Page<ProductModel> findAllByDeletedAtIsNull(Pageable pageable);
    Page<ProductModel> findAllByBrand_IdAndDeletedAtIsNull(Long brandId, Pageable pageable);
    List<ProductModel> findAllByBrand_Id(Long brandId);
}
