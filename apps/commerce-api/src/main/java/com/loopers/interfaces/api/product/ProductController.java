package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductFacade productFacade;

    @PostMapping
    public ApiResponse<ProductDto.ProductResponse> createProduct(
        @RequestBody ProductDto.CreateProductRequest request
    ) {
        ProductInfo info = productFacade.createProduct(
            request.name(),
            request.description(),
            request.price(),
            request.stock()
        );
        return ApiResponse.success(ProductDto.ProductResponse.from(info));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDto.ProductResponse> getProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        return ApiResponse.success(ProductDto.ProductResponse.from(info));
    }

    @GetMapping
    public ApiResponse<List<ProductDto.ProductResponse>> getAllProducts() {
        List<ProductInfo> infos = productFacade.getAllProducts();
        List<ProductDto.ProductResponse> responses = infos.stream()
            .map(ProductDto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductDto.ProductResponse> updateProduct(
        @PathVariable(value = "productId") Long productId,
        @RequestBody ProductDto.UpdateProductRequest request
    ) {
        ProductInfo info = productFacade.updateProduct(
            productId,
            request.name(),
            request.description(),
            request.price(),
            request.stock()
        );
        return ApiResponse.success(ProductDto.ProductResponse.from(info));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        productFacade.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}
