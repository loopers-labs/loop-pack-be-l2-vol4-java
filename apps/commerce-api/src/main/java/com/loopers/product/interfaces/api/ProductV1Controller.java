package com.loopers.product.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.product.application.ProductResult;
import com.loopers.product.application.ProductService;
import com.loopers.product.application.ProductSortOption;
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
    public ApiResponse<ProductV1Response.Detail> create(
        @Valid @RequestBody ProductV1Request.Create request
    ) {
        ProductResult.Detail result = productService.create(request.toCommand());
        return ApiResponse.success(ProductV1Response.Detail.from(result));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Response.Detail> get(@PathVariable Long productId) {
        ProductResult.Detail result = productService.get(productId);
        return ApiResponse.success(ProductV1Response.Detail.from(result));
    }

    @GetMapping
    @Override
    public ApiResponse<List<ProductV1Response.Detail>> getAll(
        @RequestParam(defaultValue = "LATEST") ProductSortOption sort
    ) {
        List<ProductV1Response.Detail> responses = productService.getAll(sort).stream()
                .map(ProductV1Response.Detail::from)
                .toList();
        return ApiResponse.success(responses);
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Response.Detail> update(
        @PathVariable Long productId,
        @Valid @RequestBody ProductV1Request.Update request
    ) {
        ProductResult.Detail result = productService.update(request.toCommand(productId));
        return ApiResponse.success(ProductV1Response.Detail.from(result));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long productId) {
        productService.delete(productId);
        return ApiResponse.success(null);
    }
}
