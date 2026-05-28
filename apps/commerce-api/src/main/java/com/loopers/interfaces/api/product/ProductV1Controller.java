package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;

    @PostMapping
    public ApiResponse<ProductV1Dto.ProductResponse> createProduct(
        @RequestBody ProductV1Dto.CreateProductRequest request
    ) {
        ProductInfo info = productFacade.createProduct(
            request.brandId(),
            request.name(),
            request.description(),
            request.price(),
            request.quantity()
        );
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }

    @GetMapping
    public ApiResponse<Page<ProductV1Dto.ProductResponse>> getAllProducts(
            @RequestParam(required = false) Long brandId
    ) {
        return ApiResponse.success(
                productFacade.getAllProducts(brandId, PageRequest.of(0, 20))
                        .map(ProductV1Dto.ProductResponse::from)
        );
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> updateProduct(
        @PathVariable(value = "productId") Long productId,
        @RequestBody ProductV1Dto.UpdateProductRequest request
    ) {
        ProductInfo info = productFacade.updateProduct(
            productId,
            request.name(),
            request.description(),
            request.price(),
            request.quantity()
        );
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        productFacade.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}
