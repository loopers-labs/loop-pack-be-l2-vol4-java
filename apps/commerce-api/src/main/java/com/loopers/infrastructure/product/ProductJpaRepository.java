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
    Page<Product> findAllByBrandId(Long brandId, Pageable pageable);

    List<Product> findAllByIdIn(List<Long> ids);

    /**
     * 비관적 쓰기 락으로 단건 조회. SELECT ... FOR UPDATE 가 발행되어
     * 트랜잭션이 끝날 때까지 다른 세션의 동시 수정/락을 차단한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /**
     * 좋아요 수(집계) 기준 내림차순 정렬. LEFT JOIN 이라 좋아요 0개인 상품도 포함된다.
     * 같은 좋아요 수면 id 역순(최신 우선)으로 tie-break.
     */
    @Query("""
        SELECT p
        FROM Product p
        LEFT JOIN com.loopers.domain.like.Like l ON l.productId = p.id
        WHERE (:brandId IS NULL OR p.brandId = :brandId)
        GROUP BY p
        ORDER BY COUNT(l) DESC, p.id DESC
    """)
    List<Product> findAllOrderByLikeCountDesc(@Param("brandId") Long brandId, Pageable pageable);
}
