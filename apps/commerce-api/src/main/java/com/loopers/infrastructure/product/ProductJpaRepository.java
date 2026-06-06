package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    List<ProductModel> findAllByBrandId(Long brandId);

    @Query("SELECT COUNT(p) > 0 FROM ProductModel p WHERE p.brandId = :brandId AND p.name.value = :name")
    boolean existsByBrandIdAndName(@Param("brandId") Long brandId, @Param("name") String name);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductModel p SET p.status = 'INACTIVE' WHERE p.brandId = :brandId")
    void suspendAllByBrandId(@Param("brandId") Long brandId);
}
