package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductCriteria;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.common.PageResult;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductApplicationService productApplicationService;

    @GetMapping
    @Override
    public ApiResponse<ProductV1Dto.PageResponse> getProducts(
            @RequestParam(value = "brandId", required = false) Long brandId,
            @RequestParam(value = "sort", defaultValue = "latest") String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PageResult<ProductInfo.ListItem> result = productApplicationService.getAllProducts(
                new ProductCriteria.GetAll(page, size, brandId, sort));
        return ApiResponse.success(ProductV1Dto.PageResponse.from(result));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.DetailResponse> getProduct(
            @PathVariable("productId") Long productId
    ) {
        ProductInfo.Detail info = productApplicationService.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.DetailResponse.from(info));
    }
}
