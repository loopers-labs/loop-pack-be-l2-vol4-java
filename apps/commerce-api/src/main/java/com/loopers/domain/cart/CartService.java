package com.loopers.domain.cart;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CartService {

    private final CartItemRepository cartItemRepository;

    @Transactional
    public CartItem addOrIncrease(Long userId, Long productId, int quantity) {
        return cartItemRepository.findByUserIdAndProductId(userId, productId)
            .map(existing -> {
                existing.increaseQuantity(quantity);
                return cartItemRepository.save(existing);
            })
            .orElseGet(() -> cartItemRepository.save(new CartItem(userId, productId, quantity)));
    }

    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(Long userId) {
        return cartItemRepository.findAllByUserId(userId);
    }

    @Transactional
    public CartItem updateQuantity(Long cartItemId, Long userId, int newQuantity) {
        CartItem cartItem = getCartItem(cartItemId);
        if (!cartItem.belongsTo(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 장바구니 항목만 수정할 수 있습니다.");
        }
        cartItem.updateQuantity(newQuantity);
        return cartItemRepository.save(cartItem);
    }

    @Transactional
    public void removeItem(Long cartItemId, Long userId) {
        CartItem cartItem = getCartItem(cartItemId);
        if (!cartItem.belongsTo(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 장바구니 항목만 삭제할 수 있습니다.");
        }
        cartItemRepository.delete(cartItemId);
    }

    @Transactional
    public void clearCart(Long userId) {
        cartItemRepository.deleteAllByUserId(userId);
    }

    private CartItem getCartItem(Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + cartItemId + "] 장바구니 항목을 찾을 수 없습니다."));
    }
}
