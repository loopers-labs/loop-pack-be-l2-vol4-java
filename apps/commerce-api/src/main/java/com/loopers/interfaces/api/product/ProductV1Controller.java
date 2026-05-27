package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductService;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.SortType;
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
 * 사용자 상품 API — 조회 전용 (인증 불필요).
 * likeCount는 products.like_count 비정규화 컬럼으로 제공.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductService productService;

    /** FR-P-02. 상품 상세 조회 */
    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        return ApiResponse.success(
            ProductV1Dto.ProductResponse.from(productService.getById(productId))
        );
    }

    /** FR-P-01. 상품 목록 조회 (brandId 필터, 정렬, 페이지네이션)
     *  sort: latest(기본) | price_asc | likes_desc
     */
    @GetMapping
    public ApiResponse<Page<ProductV1Dto.ProductResponse>> getAllProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(name = "sort", defaultValue = "latest") String sort,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        ProductSearchCondition condition = ProductSearchCondition.of(brandId, SortType.from(sort));
        return ApiResponse.success(
            productService.getAll(pageable, condition).map(ProductV1Dto.ProductResponse::from)
        );
    }
}
