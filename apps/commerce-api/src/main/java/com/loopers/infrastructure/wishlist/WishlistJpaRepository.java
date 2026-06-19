package com.loopers.infrastructure.wishlist;

import com.loopers.domain.wishlist.WishlistModel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WishlistJpaRepository extends JpaRepository<WishlistModel, Long> {

    Optional<WishlistModel> findByUserIdAndProductId(Long userId, Long productId);

    List<WishlistModel> findAllByUserId(Long userId);

}
