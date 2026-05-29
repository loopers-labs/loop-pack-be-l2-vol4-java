package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductApplicationService productApplicationService;

    @GetMapping
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false) String sort,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        List<ProductV1Dto.ProductResponse> responses = productApplicationService.getProducts(brandId, page, size, sort).stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(@PathVariable @Min(1) Long id) {
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(productApplicationService.getProduct(id)));
    }
}
