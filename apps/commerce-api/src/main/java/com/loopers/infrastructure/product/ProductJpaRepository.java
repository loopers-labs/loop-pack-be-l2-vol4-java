package com.loopers.infrastructure.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findByBrandIdAndDeletedAtIsNull(Long brandId);

    List<ProductEntity> findByDeletedAtIsNull(Pageable pageable);

    List<ProductEntity> findByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    List<ProductEntity> findByIdInAndDeletedAtIsNull(Collection<Long> ids);

    /**
     * 좋아요 수 원자적 +1 (DB에서 직접 증가 → read-modify-write의 lost update 차단).
     * 서로 다른 사용자가 같은 상품을 동시에 좋아요해도 카운터가 정확히 반영된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductEntity p set p.likesCount = p.likesCount + 1 where p.id = :id")
    int incrementLikesCount(@Param("id") Long id);

    /**
     * 좋아요 수 원자적 -1. likes_count > 0 가드로 음수 방지 (이미 0이면 영향 행 0 = no-op).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductEntity p set p.likesCount = p.likesCount - 1 where p.id = :id and p.likesCount > 0")
    int decrementLikesCount(@Param("id") Long id);
}
