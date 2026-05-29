package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductStockModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductStockJpaRepository extends JpaRepository<ProductStockModel, Long> {

    @EntityGraph(attributePaths = {"product"})
    Optional<ProductStockModel> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"product"})
    @Query("SELECT s FROM ProductStockModel s WHERE s.id = :id")
    Optional<ProductStockModel> findByIdForUpdate(@Param("id") Long id);

    List<ProductStockModel> findAllByProductId(Long productId);
}
