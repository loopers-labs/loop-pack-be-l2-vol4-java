package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemJpaRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);
    List<CartItem> findAllByUserId(Long userId);
    void deleteAllByUserId(Long userId);
}
