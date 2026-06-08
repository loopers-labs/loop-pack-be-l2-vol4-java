package com.loopers.infrastructure.like;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeEntity, Long> {
    Optional<LikeEntity> findByUserIdAndProductId(Long userId, Long productId);

    /**
     * 비활성→활성 전이를 원자적으로 (취소된 좋아요 재등록). deletedAt이 NOT NULL인 경우에만 영향.
     * 영향 행 수(0/1)로 "이 트랜잭션이 실제 전이했는지"를 판정해, 동시 reactivate에서 카운터 이중 증가를 막는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LikeEntity l set l.deletedAt = null, l.likedAt = :likedAt " +
           "where l.userId = :userId and l.productId = :productId and l.deletedAt is not null")
    int activate(@Param("userId") Long userId, @Param("productId") Long productId,
                 @Param("likedAt") ZonedDateTime likedAt);

    /**
     * 활성→비활성 전이를 원자적으로 (좋아요 취소). deletedAt이 NULL인 경우에만 영향.
     * 영향 행 수로 실제 전이 여부를 판정해 동시 unlike에서 카운터 이중 차감을 막는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LikeEntity l set l.deletedAt = :deletedAt " +
           "where l.userId = :userId and l.productId = :productId and l.deletedAt is null")
    int deactivate(@Param("userId") Long userId, @Param("productId") Long productId,
                   @Param("deletedAt") ZonedDateTime deletedAt);

    List<LikeEntity> findByProductIdAndDeletedAtIsNull(Long productId);

    List<LikeEntity> findByUserIdAndDeletedAtIsNullOrderByLikedAtDescIdDesc(Long userId, Pageable pageable);

    @Query("select l.productId from LikeEntity l " +
           "where l.userId = :userId and l.productId in :productIds and l.deletedAt is null")
    List<Long> findLikedProductIds(@Param("userId") Long userId, @Param("productIds") Collection<Long> productIds);
}
