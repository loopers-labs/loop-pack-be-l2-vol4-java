package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    List<Product> findAllByIdInAndDeletedAtIsNull(Collection<Long> ids);

    Page<Product> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    // clearAutomatically 를 두지 않는다 — 차감 후 같은 트랜잭션에서 이 상품의 stock 을 다시 읽지 않으므로
    // 1차 캐시 stale 위험이 없고, 컨텍스트 전체 clear 가 일으키는 부수효과(쿠폰 등 다른 managed 엔티티 detach)를 피한다.
    @Modifying
    @Query("UPDATE Product p SET p.stock.quantity = p.stock.quantity - :qty " +
            "WHERE p.id = :id AND p.stock.quantity >= :qty")
    int decreaseStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Product p
        SET p.deletedAt = CURRENT_TIMESTAMP,
            p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.brandId = :brandId AND p.deletedAt IS NULL
    """)
    int bulkSoftDeleteByBrandId(Long brandId);

    @Query("""
        SELECT p
        FROM Product p
        LEFT JOIN ProductLike l ON l.productId = p.id
        WHERE p.deletedAt IS NULL
        GROUP BY p
        ORDER BY COUNT(l) DESC, p.id DESC
    """)
    Page<Product> findAllOrderByLikesDesc(Pageable pageable);

    @Query("""
        SELECT p
        FROM Product p
        LEFT JOIN ProductLike l ON l.productId = p.id
        WHERE p.deletedAt IS NULL AND p.brandId = :brandId
        GROUP BY p
        ORDER BY COUNT(l) DESC, p.id DESC
    """)
    Page<Product> findAllByBrandIdOrderByLikesDesc(Long brandId, Pageable pageable);
}
