package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductLikeViewModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductLikeViewJpaRepository extends JpaRepository<ProductLikeViewModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT plv FROM ProductLikeViewModel plv WHERE plv.productId = :productId")
    Optional<ProductLikeViewModel> findByProductIdForUpdate(@Param("productId") Long productId);

    List<ProductLikeViewModel> findAllByProductIdIn(List<Long> productIds);

    void deleteByProductId(Long productId);
}
