package com.loopers.like.infrastructure;

import com.loopers.like.domain.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);

    long countByProductId(Long productId);

    @Query("""
        select l.productId as productId, count(l) as likeCount
        from Like l
        where l.productId in :productIds
        group by l.productId
        """)
    List<LikeCount> countByProductIds(@Param("productIds") Collection<Long> productIds);

    interface LikeCount {
        Long getProductId();
        Long getLikeCount();
    }
}
