package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductSort;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.event.product.ProductViewedEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class ProductController {

    private final ProductFacade productFacade;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Public API ───────────────────────────────────────────

    @GetMapping("/api/v1/products")
    public ApiResponse<ProductDto.ProductPageResponse> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "latest") ProductSort sort,
        @RequestParam(required = false) Long minPrice,
        @RequestParam(required = false) Long maxPrice,
        @RequestParam(required = false) Boolean inStock,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProductInfo> result = productFacade.getProducts(brandId, sort, minPrice, maxPrice, inStock, page, size);
        List<ProductDto.ProductResponse> products = result.getContent().stream()
            .map(ProductDto.ProductResponse::from)
            .toList();
        return ApiResponse.success(new ProductDto.ProductPageResponse(products, result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/api/v1/products/{productId}")
    public ApiResponse<ProductDto.ProductResponse> getProduct(
        @PathVariable Long productId,
        @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        eventPublisher.publishEvent(new ProductViewedEvent(productId, userId));
        return ApiResponse.success(ProductDto.ProductResponse.from(info));
    }

    // ─── Admin API ────────────────────────────────────────────

    @GetMapping("/api-admin/v1/products")
    public ApiResponse<ProductDto.ProductPageResponse> getProductsAdmin(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "latest") ProductSort sort,
        @RequestParam(required = false) Long minPrice,
        @RequestParam(required = false) Long maxPrice,
        @RequestParam(required = false) Boolean inStock,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProductInfo> result = productFacade.getProducts(brandId, sort, minPrice, maxPrice, inStock, page, size);
        List<ProductDto.ProductResponse> products = result.getContent().stream()
            .map(ProductDto.ProductResponse::from)
            .toList();
        return ApiResponse.success(new ProductDto.ProductPageResponse(products, result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/api-admin/v1/products/{productId}")
    public ApiResponse<ProductDto.ProductResponse> getProductAdmin(@PathVariable Long productId) {
        ProductInfo info = productFacade.getProduct(productId);
        return ApiResponse.success(ProductDto.ProductResponse.from(info));
    }

    @PostMapping("/api-admin/v1/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductDto.ProductResponse> createProduct(@Valid @RequestBody ProductDto.CreateRequest request) {
        ProductInfo info = productFacade.createProduct(request.name(), request.price(), request.brandId(), request.stockQuantity());
        return ApiResponse.success(ProductDto.ProductResponse.from(info));
    }

    @PutMapping("/api-admin/v1/products/{productId}")
    public ApiResponse<ProductDto.ProductResponse> updateProduct(
        @PathVariable Long productId,
        @Valid @RequestBody ProductDto.UpdateRequest request
    ) {
        ProductInfo info = productFacade.updateProduct(productId, request.name(), request.price());
        return ApiResponse.success(ProductDto.ProductResponse.from(info));
    }

    @DeleteMapping("/api-admin/v1/products/{productId}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long productId) {
        productFacade.deleteProduct(productId);
        return ApiResponse.success(null);
    }

    @PutMapping("/api-admin/v1/products/{productId}/stock")
    public ApiResponse<Void> updateStock(
        @PathVariable Long productId,
        @Valid @RequestBody ProductDto.StockUpdateRequest request
    ) {
        productFacade.updateStock(productId, request.quantity());
        return ApiResponse.success(null);
    }
}
