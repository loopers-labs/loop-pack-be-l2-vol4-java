package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CartItemRepositoryImpl implements CartItemRepository {

    private final CartItemJpaRepository cartItemJpaRepository;

    @Override
    public CartItem save(CartItem cartItem) {
        return cartItemJpaRepository.save(cartItem);
    }

    @Override
    public Optional<CartItem> findById(Long id) {
        return cartItemJpaRepository.findById(id);
    }

    @Override
    public Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId) {
        return cartItemJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public List<CartItem> findAllByUserId(Long userId) {
        return cartItemJpaRepository.findAllByUserId(userId);
    }

    @Override
    public void delete(Long id) {
        cartItemJpaRepository.deleteById(id);
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        cartItemJpaRepository.deleteAllByUserId(userId);
    }
}
