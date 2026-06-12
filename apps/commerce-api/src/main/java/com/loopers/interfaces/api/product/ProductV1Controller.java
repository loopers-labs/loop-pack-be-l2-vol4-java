package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.domain.product.ProductSort;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductApplicationService productApplicationService;

    @GetMapping
    public ApiResponse<PageResult<ProductV1Dto.PlpResponse>> getAllProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        ProductSort productSort = ProductSort.from(sort);
        return ApiResponse.success(
                PageResult.from(
                        productApplicationService.getAllProducts(brandId, PageRequest.of(page, size, productSort.toSort()))
                                .map(ProductV1Dto.PlpResponse::from)
                )
        );
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.PdpResponse> getProduct(
            @PathVariable Long productId
    ) {
        return ApiResponse.success(ProductV1Dto.PdpResponse.from(productApplicationService.getProduct(productId)));
    }
}
