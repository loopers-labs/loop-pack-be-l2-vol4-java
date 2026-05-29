package com.loopers.infrastructure.wishlist;

import com.loopers.domain.wishlist.WishlistModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WishlistJpaRepository extends JpaRepository<WishlistModel, Long> {

    Optional<WishlistModel> findByUserIdAndProductId(Long userId, Long productId);

    List<WishlistModel> findAllByUserId(Long userId);
    long countByProductId(Long productId);

    @Query("SELECT w.productId, COUNT(w) FROM WishlistModel w WHERE w.productId IN :productIds GROUP BY w.productId")
    List<Object[]> countGroupByProductIds(@Param("productIds") List<Long> productIds);
}
