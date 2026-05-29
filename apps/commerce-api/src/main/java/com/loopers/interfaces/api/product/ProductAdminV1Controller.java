package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller {

    private final ProductApplicationService productApplicationService;

    @GetMapping
    public ApiResponse<List<ProductAdminV1Dto.ProductResponse>> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
        @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.") @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    ) {
        List<ProductAdminV1Dto.ProductResponse> responses = productApplicationService.getProductsForAdmin(brandId, page, size).stream()
            .map(ProductAdminV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductAdminV1Dto.ProductResponse> getProduct(@PathVariable @Min(1) Long id) {
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(productApplicationService.getProductForAdmin(id)));
    }

    @PostMapping
    public ApiResponse<ProductAdminV1Dto.ProductResponse> createProduct(@RequestBody @Valid ProductAdminV1Dto.CreateProductRequest request) {
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(
            productApplicationService.createProduct(request.brandId(), request.name(), request.description(), request.price(), request.stock())
        ));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductAdminV1Dto.ProductResponse> updateProduct(@PathVariable @Min(1) Long id, @RequestBody @Valid ProductAdminV1Dto.UpdateProductRequest request) {
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(
            productApplicationService.updateProduct(id, request.name(), request.description(), request.price(), request.stock())
        ));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable @Min(1) Long id) {
        productApplicationService.deleteProduct(id);
        return ApiResponse.success(null);
    }
}
