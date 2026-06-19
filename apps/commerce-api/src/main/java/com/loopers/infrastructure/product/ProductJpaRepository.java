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
     * stat 의 (brand_id, like_count) 인덱스가 필터+정렬을 한 번에 커버한다.
     *
     * LEFT JOIN + COALESCE: 신규 상품처럼 stat 이 아직 없을 수 있는 상품도 결과에서 누락되지 않게 한다
     * (stat 없으면 like_count = 0 으로 처리). brandId 필터는 stat 이 아닌 Product 기준으로.
     */
    @Query("""
        SELECT p
        FROM Product p
        LEFT JOIN ProductLikeStat s ON s.productId = p.id
        WHERE (:brandId IS NULL OR p.brandId = :brandId)
        ORDER BY COALESCE(s.likeCount, 0) DESC, p.id DESC
    """)
    List<Product> findAllOrderByLikeCountDesc(@Param("brandId") Long brandId, Pageable pageable);
}
