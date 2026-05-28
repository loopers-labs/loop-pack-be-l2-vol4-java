package com.loopers.product.interfaces.api;

import com.loopers.common.interfaces.api.ApiResponse;
import com.loopers.product.application.ProductFacade;
import com.loopers.product.application.ProductInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            request.name(),
            request.description(),
            request.price(),
            request.stock()
        );
        ProductV1Dto.ProductResponse response = ProductV1Dto.ProductResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        ProductV1Dto.ProductResponse response = ProductV1Dto.ProductResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getAllProducts() {
        List<ProductInfo> infos = productFacade.getAllProducts();
        List<ProductV1Dto.ProductResponse> responses = infos.stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
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
            request.stock()
        );
        ProductV1Dto.ProductResponse response = ProductV1Dto.ProductResponse.from(info);
        return ApiResponse.success(response);
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        productFacade.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}
