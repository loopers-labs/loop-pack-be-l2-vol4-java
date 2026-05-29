package com.loopers.interfaces.api.cart;

import com.loopers.application.cart.CartInfo;

public class CartDto {

    public record AddItemRequest(Long productId, int quantity) {}

    public record UpdateQuantityRequest(int quantity) {}

    public record CartItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        Long price,
        int quantity
    ) {
        public static CartItemResponse from(CartInfo info) {
            return new CartItemResponse(
                info.cartItemId(),
                info.productId(),
                info.productName(),
                info.price(),
                info.quantity()
            );
        }
    }
}
