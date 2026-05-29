package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.SortType;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductFacade productFacade;

    @PostMapping
    public ApiResponse<ProductDto.ProductResponse> createProduct(
        @RequestBody ProductDto.CreateProductRequest request
    ) {
        ProductInfo info = productFacade.createProduct(
            request.brandId(),
            request.name(),
            request.description(),
            request.price(),
            request.stock()
        );
        ProductDto.ProductResponse response = ProductDto.ProductResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDto.ProductResponse> getProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        ProductDto.ProductResponse response = ProductDto.ProductResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<List<ProductDto.ProductResponse>> getAllProducts(
        @RequestParam(defaultValue = "LATEST") SortType sort
    ) {
        List<ProductInfo> infos = productFacade.getAllProducts(sort);
        return ApiResponse.success(infos.stream().map(ProductDto.ProductResponse::from).toList());
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
            request.price()
        );
        ProductDto.ProductResponse response = ProductDto.ProductResponse.from(info);
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
