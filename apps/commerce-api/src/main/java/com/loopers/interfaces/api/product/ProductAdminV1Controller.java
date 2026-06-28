package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<Page<ProductV1Dto.ProductResponse>> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false, defaultValue = "LATEST") ProductSortType sort,
        Pageable pageable
    ) {
        Page<ProductInfo> infos = productFacade.getProducts(brandId, sort, pageable);
        return ApiResponse.success(infos.map(ProductV1Dto.ProductResponse::from));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(productFacade.getProduct(productId)));
    }

    @PostMapping
    public ApiResponse<ProductV1Dto.ProductResponse> createProduct(
        @RequestBody ProductV1Dto.CreateProductRequest request
    ) {
        ProductInfo info = productFacade.createProduct(request.name(), request.description(), request.price(), request.stock(), request.brandId());
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> updateProduct(
        @PathVariable Long productId,
        @RequestBody ProductV1Dto.UpdateProductRequest request
    ) {
        ProductInfo info = productFacade.updateProduct(productId, request.name(), request.description(), request.price(), request.stock());
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long productId) {
        productFacade.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}
