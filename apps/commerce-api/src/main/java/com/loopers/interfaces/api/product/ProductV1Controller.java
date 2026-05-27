package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductService;
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

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductService productService;

    @PostMapping
    public ApiResponse<ProductV1Dto.ProductResponse> createProduct(
        @RequestBody ProductV1Dto.CreateProductRequest request
    ) {
        ProductInfo info = ProductInfo.from(productService.createProduct(
            request.name(), request.description(), request.price(), request.stock()
        ));
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductInfo info = ProductInfo.from(productService.getProduct(productId));
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }

    @GetMapping
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getAllProducts() {
        List<ProductV1Dto.ProductResponse> responses = productService.getAllProducts().stream()
            .map(ProductInfo::from)
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> updateProduct(
        @PathVariable Long productId,
        @RequestBody ProductV1Dto.UpdateProductRequest request
    ) {
        ProductInfo info = ProductInfo.from(productService.updateProduct(
            productId, request.name(), request.description(), request.price(), request.stock()
        ));
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long productId) {
        productService.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}
