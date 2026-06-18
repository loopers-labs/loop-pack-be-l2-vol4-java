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
     * 좋아요 수 기준 내림차순 정렬.
     *
     * Week 5: product_like 집계(JOIN+GROUP BY) 대신 ProductLikeStat read-model 의 like_count 컬럼을 사용한다.
     * stat 의 (brand_id, like_count) 인덱스가 필터+정렬을 한 번에 커버하므로 temp+filesort 가 제거된다.
     * 같은 좋아요 수면 id 역순(최신 우선)으로 tie-break.
     */
    @Query("""
        SELECT p
        FROM Product p
        JOIN ProductLikeStat s ON s.productId = p.id
        WHERE (:brandId IS NULL OR s.brandId = :brandId)
        ORDER BY s.likeCount DESC, p.id DESC
    """)
    List<Product> findAllOrderByLikeCountDesc(@Param("brandId") Long brandId, Pageable pageable);
}
