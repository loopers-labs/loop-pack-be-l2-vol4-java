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

    // 재고가 Inventory 로 분리되어 주문 경로가 products 행을 잠글 이유가 없다(비-락 조회).
    List<Product> findAllByIdInAndDeletedAtIsNull(Collection<Long> ids);

    Page<Product> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    // 좋아요 수 원자적 증감 — 검증/증감 사이 간극이 없어 카운터 lost update 를 원천 차단한다.
    // 증감 여부 게이트(행이 실제로 INSERT/DELETE 됐는가)는 응용에서 affected rows 로 판정한다.
    @Modifying
    @Query("UPDATE Product p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    int increaseLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Product p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    int decreaseLikeCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Product p
        SET p.deletedAt = CURRENT_TIMESTAMP,
            p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.brandId = :brandId AND p.deletedAt IS NULL
    """)
    int bulkSoftDeleteByBrandId(Long brandId);

    // 브랜드 삭제 cascade 시, 소프트 삭제 '전에' 대상 상품 id 를 모아 재고 cascade 에 넘긴다.
    @Query("SELECT p.id FROM Product p WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    List<Long> findIdsByBrandIdAndDeletedAtIsNull(@Param("brandId") Long brandId);
}
