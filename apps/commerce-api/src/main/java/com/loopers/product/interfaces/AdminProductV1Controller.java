package com.loopers.product.interfaces;

import com.loopers.product.application.ProductFacade;
import com.loopers.product.application.ProductInfo;
import com.loopers.support.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductV1Controller {

    private final ProductFacade productFacade;

    @PostMapping
    public ApiResponse<AdminProductV1Dto.ProductResponse> createProduct(
        @RequestBody AdminProductV1Dto.CreateRequest request
    ) {
        ProductInfo info = productFacade.createProduct(
            request.name(), request.description(), request.price(), request.stock(), request.brandId()
        );
        return ApiResponse.success(AdminProductV1Dto.ProductResponse.from(info));
    }

    @PatchMapping("/{productId}")
    public ApiResponse<AdminProductV1Dto.ProductResponse> updateProduct(
        @PathVariable Long productId,
        @RequestBody AdminProductV1Dto.UpdateRequest request
    ) {
        ProductInfo info = productFacade.updateProduct(
            productId, request.name(), request.description(), request.price()
        );
        return ApiResponse.success(AdminProductV1Dto.ProductResponse.from(info));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long productId) {
        productFacade.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}
