package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 상품 API — 조회 전용.
 * 등록·수정·삭제는 ProductAdminV1Controller 로 분리 예정.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductService productService;

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(productService.getById(productId)));
    }

    @GetMapping
    public ApiResponse<Page<ProductV1Dto.ProductResponse>> getAllProducts(
        @RequestParam(required = false) Long brandId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
            productService.getAll(pageable, brandId)
                .map(ProductV1Dto.ProductResponse::from)
        );
    }
}
