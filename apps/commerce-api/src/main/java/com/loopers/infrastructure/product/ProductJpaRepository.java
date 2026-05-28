package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);
    List<ProductModel> findAllByIdInAndDeletedAtIsNull(Collection<Long> ids);
    boolean existsByIdAndDeletedAtIsNull(Long id);
    long countByBrandIdAndDeletedAtIsNull(Long brandId);

    @Query("SELECT p.brandId, COUNT(p) FROM ProductModel p WHERE p.deletedAt IS NULL AND p.brandId IN :brandIds GROUP BY p.brandId")
    List<Object[]> countGroupByBrandIdAndDeletedAtIsNull(@Param("brandIds") Collection<Long> brandIds);

    Page<ProductModel> findAllByDeletedAtIsNull(Pageable pageable);

    Page<ProductModel> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    /**
     * flushAutomatically=true — 같은 트랜잭션 내 pending 변경(예: like row DELETE)을 먼저 flush한 뒤 UPDATE 실행. 미설정 시 detach로 인해 pending DELETE가 누락된다.
     * clearAutomatically=true — UPDATE 이후 1차 캐시 비워 stale read 방지.
     * @Transactional — 테스트 등 비-서비스 호출자가 직접 호출할 때도 트랜잭션을 보장한다.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id AND p.deletedAt IS NULL")
    int incrementLikeCount(@Param("id") Long id);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.deletedAt IS NULL AND p.likeCount > 0")
    int decrementLikeCount(@Param("id") Long id);
}
