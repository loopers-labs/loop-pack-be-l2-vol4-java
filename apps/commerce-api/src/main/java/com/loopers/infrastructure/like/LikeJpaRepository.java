package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Modifying
    @Query("delete from LikeModel l where l.userId = :userId and l.productId = :productId")
    int deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    @Query("""
        select p
        from LikeModel l, ProductModel p
        where l.userId = :userId
          and l.productId = p.id
          and p.deletedAt is null
        order by l.createdAt desc, l.id desc
        """)
    List<ProductModel> findLikedActiveProductsByUserId(@Param("userId") Long userId, Pageable pageable);
}
