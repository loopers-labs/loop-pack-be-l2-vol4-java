package com.loopers.product.infrastructure;

import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductSummaryModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);

    @Query("SELECT NEW com.loopers.product.domain.ProductSummaryModel(p.id, p.name, p.price, p.brandId, p.likeCount) FROM ProductModel p WHERE p.deletedAt IS NULL")
    List<ProductSummaryModel> findAllActive(Pageable pageable);

    @Query("SELECT NEW com.loopers.product.domain.ProductSummaryModel(p.id, p.name, p.price, p.brandId, p.likeCount) FROM ProductModel p WHERE p.deletedAt IS NULL AND p.brandId = :brandId")
    List<ProductSummaryModel> findAllActiveByBrandId(@Param("brandId") Long brandId, Pageable pageable);

    @Query("""
        SELECT NEW com.loopers.product.domain.ProductSummaryModel(p.id, p.name, p.price, p.brandId, p.likeCount) FROM ProductModel p WHERE p.deletedAt IS NULL
        AND EXISTS (SELECT 1 FROM StockModel s WHERE s.productId = p.id AND (s.totalStock - s.reservedStock) > 0)
        """)
    List<ProductSummaryModel> findAllActiveInStock(Pageable pageable);

    @Query("""
        SELECT NEW com.loopers.product.domain.ProductSummaryModel(p.id, p.name, p.price, p.brandId, p.likeCount) FROM ProductModel p WHERE p.deletedAt IS NULL AND p.brandId = :brandId
        AND EXISTS (SELECT 1 FROM StockModel s WHERE s.productId = p.id AND (s.totalStock - s.reservedStock) > 0)
        """)
    List<ProductSummaryModel> findAllActiveByBrandIdInStock(@Param("brandId") Long brandId, Pageable pageable);

    @Query("SELECT p FROM ProductModel p WHERE p.id IN :ids AND p.deletedAt IS NULL")
    List<ProductModel> findAllByIds(@Param("ids") List<Long> ids);

    @Query("SELECT p FROM ProductModel p WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    List<ProductModel> findAllByBrandId(@Param("brandId") Long brandId);

    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    void decrementLikeCount(@Param("id") Long id);
}
