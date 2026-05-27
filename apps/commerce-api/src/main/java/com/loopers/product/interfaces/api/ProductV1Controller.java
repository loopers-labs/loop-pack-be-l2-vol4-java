package com.loopers.product.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.product.application.ProductService;
import com.loopers.product.application.ProductSortOption;
import com.loopers.product.domain.Product;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductService productService;

    @PostMapping
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> create(
        @Valid @RequestBody ProductV1Dto.CreateRequest request
    ) {
        Product product = productService.create(request.toCommand());
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(product));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> get(@PathVariable Long productId) {
        Product product = productService.get(productId);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(product));
    }

    @GetMapping
    @Override
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getAll(
        @RequestParam(defaultValue = "LATEST") ProductSortOption sort
    ) {
        List<ProductV1Dto.ProductResponse> responses = productService.getAll(sort).stream()
                .map(ProductV1Dto.ProductResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> update(
        @PathVariable Long productId,
        @Valid @RequestBody ProductV1Dto.UpdateRequest request
    ) {
        Product product = productService.update(request.toCommand(productId));
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(product));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long productId) {
        productService.delete(productId);
        return ApiResponse.success(null);
    }
}
