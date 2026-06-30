package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);
    List<Product> findByIdInAndDeletedAtIsNull(List<Long> ids);
    Page<Product> findByDeletedAtIsNull(Pageable pageable);
    Page<Product> findByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    // likes_desc 정렬: like 도메인의 집계 테이블과 무연관 조인 (좋아요 0개 상품은 COALESCE 0)
    @Query("""
        SELECT p FROM Product p
        LEFT JOIN ProductLikeCount plc ON plc.productId = p.id
        WHERE p.deletedAt IS NULL
        ORDER BY COALESCE(plc.count, 0) DESC, p.id DESC
        """)
    Page<Product> findAllOrderByLikesDesc(Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN ProductLikeCount plc ON plc.productId = p.id
        WHERE p.deletedAt IS NULL AND p.brandId = :brandId
        ORDER BY COALESCE(plc.count, 0) DESC, p.id DESC
        """)
    Page<Product> findByBrandIdOrderByLikesDesc(@Param("brandId") Long brandId, Pageable pageable);

    // 비관적 락 (SELECT ... FOR UPDATE). id 오름차순 잠금으로 교차 데드락 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids AND p.deletedAt IS NULL ORDER BY p.id ASC")
    List<Product> findAllByIdInForUpdate(@Param("ids") List<Long> ids);
}
