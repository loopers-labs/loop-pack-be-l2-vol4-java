package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);
    Page<ProductModel> findByDeletedAtIsNull(Pageable pageable);
    Page<ProductModel> findByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    // likes_desc 정렬: like 도메인의 집계 테이블과 무연관 조인 (좋아요 0개 상품은 COALESCE 0)
    @Query("""
        SELECT p FROM ProductModel p
        LEFT JOIN ProductLikeCount plc ON plc.productId = p.id
        WHERE p.deletedAt IS NULL
        ORDER BY COALESCE(plc.count, 0) DESC, p.id DESC
        """)
    Page<ProductModel> findAllOrderByLikesDesc(Pageable pageable);

    @Query("""
        SELECT p FROM ProductModel p
        LEFT JOIN ProductLikeCount plc ON plc.productId = p.id
        WHERE p.deletedAt IS NULL AND p.brandId = :brandId
        ORDER BY COALESCE(plc.count, 0) DESC, p.id DESC
        """)
    Page<ProductModel> findByBrandIdOrderByLikesDesc(@Param("brandId") Long brandId, Pageable pageable);

    // 비관적 락 (SELECT ... FOR UPDATE). id 오름차순 잠금으로 교차 데드락 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductModel p WHERE p.id IN :ids AND p.deletedAt IS NULL ORDER BY p.id ASC")
    List<ProductModel> findAllByIdInForUpdate(@Param("ids") List<Long> ids);
}
