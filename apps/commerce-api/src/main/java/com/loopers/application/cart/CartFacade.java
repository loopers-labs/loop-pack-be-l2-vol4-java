package com.loopers.application.cart;

import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductReader;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CartFacade {

    private final CartService cartService;
    private final ProductReader productReader;

    @Transactional
    public CartInfo addItem(Long userId, Long productId, int quantity) {
        if (!productReader.existsProduct(productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다.");
        }
        CartItem cartItem = cartService.addOrIncrease(userId, productId, quantity);
        ProductModel product = productReader.getProduct(productId);
        return CartInfo.of(cartItem, product);
    }

    @Transactional(readOnly = true)
    public List<CartInfo> getCartItems(Long userId) {
        return cartService.getCartItems(userId).stream()
            .map(item -> CartInfo.of(item, productReader.getProduct(item.getProductId())))
            .toList();
    }

    @Transactional
    public CartInfo updateQuantity(Long cartItemId, Long userId, int newQuantity) {
        CartItem cartItem = cartService.updateQuantity(cartItemId, userId, newQuantity);
        ProductModel product = productReader.getProduct(cartItem.getProductId());
        return CartInfo.of(cartItem, product);
    }

    @Transactional
    public void removeItem(Long cartItemId, Long userId) {
        cartService.removeItem(cartItemId, userId);
    }

    @Transactional
    public void clearCart(Long userId) {
        cartService.clearCart(userId);
    }
}
