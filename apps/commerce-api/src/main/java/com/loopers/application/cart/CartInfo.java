package com.loopers.application.cart;

import com.loopers.domain.cart.CartItem;
import com.loopers.domain.product.ProductModel;

public record CartInfo(
    Long cartItemId,
    Long productId,
    String productName,
    Long price,
    int quantity
) {
    public static CartInfo of(CartItem cartItem, ProductModel product) {
        return new CartInfo(
            cartItem.getId(),
            cartItem.getProductId(),
            product.getName(),
            product.getPrice(),
            cartItem.getQuantity()
        );
    }
}
