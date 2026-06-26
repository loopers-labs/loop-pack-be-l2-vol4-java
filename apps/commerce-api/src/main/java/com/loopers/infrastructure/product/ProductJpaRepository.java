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
     * tie-break 도 stat 의 productId(=PK) 로 잡아 mixed-table ordering 을 피하고 인덱스로 정렬한다.
     * → temp + filesort 제거, Backward index scan; Using index 활용.
     *
     * INNER JOIN: ProductService.createProduct 에서 stat 도 0 으로 같이 init 되므로 누락 우려 없음.
     * (백필 안 된 운영 케이스는 별도 배치로 보강하는 영역)
     */
    @Query("""
        SELECT p
        FROM Product p
        JOIN ProductLikeStat s ON s.productId = p.id
        WHERE (:brandId IS NULL OR s.brandId = :brandId)
        ORDER BY s.likeCount DESC, s.productId DESC
    """)
    List<Product> findAllOrderByLikeCountDesc(@Param("brandId") Long brandId, Pageable pageable);
}
