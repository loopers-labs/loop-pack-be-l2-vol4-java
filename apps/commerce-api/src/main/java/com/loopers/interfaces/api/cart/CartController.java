package com.loopers.interfaces.api.cart;

import com.loopers.application.cart.CartFacade;
import com.loopers.application.cart.CartInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartFacade cartFacade;

    @PostMapping
    public ApiResponse<CartDto.CartItemResponse> addItem(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestBody CartDto.AddItemRequest request
    ) {
        CartInfo info = cartFacade.addItem(principal.getId(), request.productId(), request.quantity());
        return ApiResponse.success(CartDto.CartItemResponse.from(info));
    }

    @GetMapping
    public ApiResponse<List<CartDto.CartItemResponse>> getCartItems(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<CartInfo> items = cartFacade.getCartItems(principal.getId());
        return ApiResponse.success(items.stream().map(CartDto.CartItemResponse::from).toList());
    }

    @PutMapping("/{cartItemId}")
    public ApiResponse<CartDto.CartItemResponse> updateQuantity(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long cartItemId,
        @RequestBody CartDto.UpdateQuantityRequest request
    ) {
        CartInfo info = cartFacade.updateQuantity(cartItemId, principal.getId(), request.quantity());
        return ApiResponse.success(CartDto.CartItemResponse.from(info));
    }

    @DeleteMapping("/{cartItemId}")
    public ApiResponse<Void> removeItem(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long cartItemId
    ) {
        cartFacade.removeItem(cartItemId, principal.getId());
        return ApiResponse.success(null);
    }

    @DeleteMapping
    public ApiResponse<Void> clearCart(@AuthenticationPrincipal UserPrincipal principal) {
        cartFacade.clearCart(principal.getId());
        return ApiResponse.success(null);
    }
}
