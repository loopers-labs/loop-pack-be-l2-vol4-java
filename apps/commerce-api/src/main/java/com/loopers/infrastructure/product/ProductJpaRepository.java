package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);

    List<ProductModel> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    @Query("""
        select p from ProductModel p
         where p.deletedAt is null
           and (:brandId is null or p.brandId = :brandId)
        """)
    List<ProductModel> findActivePage(@Param("brandId") Long brandId, Pageable pageable);

    @Query("""
        select p from ProductModel p
         where (:brandId is null or p.brandId = :brandId)
        """)
    Page<ProductModel> findAllPage(@Param("brandId") Long brandId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ProductModel p
           set p.likeCount = p.likeCount + 1
         where p.id = :id
           and p.deletedAt is null
        """)
    int incrementLikeCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ProductModel p
           set p.likeCount = p.likeCount - 1
         where p.id = :id
           and p.deletedAt is null
        """)
    int decrementLikeCount(@Param("id") Long id);
}
