package com.loopers.interfaces.api.admin.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// TODO: 관리자 기능으로 변경될 것
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/products")
public class AdminProductV1Controller {

    private final ProductFacade productFacade;

    @PostMapping
    public ApiResponse<AdminProductV1Dto.ProductResponse> createProduct(
            @RequestBody AdminProductV1Dto.CreateProductRequest request
    ) {
        ProductInfo info = productFacade.createProduct(
                request.brandId(),
                request.name(),
                request.description(),
                request.price(),
                request.stock(),
                request.status()
        );
        return ApiResponse.success(AdminProductV1Dto.ProductResponse.from(info));
    }

    @GetMapping("/{productId}")
    public ApiResponse<AdminProductV1Dto.ProductResponse> getProduct(
            @PathVariable(value = "productId") Long productId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        return ApiResponse.success(AdminProductV1Dto.ProductResponse.from(info));
    }

    @PutMapping("/{productId}")
    public ApiResponse<AdminProductV1Dto.ProductResponse> updateProduct(
            @PathVariable(value = "productId") Long productId,
            @RequestBody AdminProductV1Dto.UpdateProductRequest request
    ) {
        ProductInfo info = productFacade.updateProduct(
                productId,
                request.brandId(),
                request.name(),
                request.description(),
                request.price(),
                request.stock(),
                request.status()
        );
        return ApiResponse.success(AdminProductV1Dto.ProductResponse.from(info));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
            @PathVariable(value = "productId") Long productId
    ) {
        productFacade.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}