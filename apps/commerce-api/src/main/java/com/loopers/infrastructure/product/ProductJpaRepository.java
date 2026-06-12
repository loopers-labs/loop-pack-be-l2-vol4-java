package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    List<ProductModel> findAllByBrandId(Long brandId, Sort sort);

    /**
     * 좋아요 카운트 원자적 증가 — lost update 방지.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    /**
     * 좋아요 카운트 원자적 감소 — 0 미만 방지 (음수 보호).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    int decrementLikeCount(@Param("id") Long id);
}
