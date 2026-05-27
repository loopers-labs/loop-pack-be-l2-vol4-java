package com.loopers.interfaces.api.product;

import com.loopers.application.like.LikeService;
import com.loopers.application.product.ProductInfo;
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

import java.util.List;
import java.util.Map;

/**
 * 사용자 상품 API — 조회 전용 (인증 불필요).
 * 등록·수정·삭제는 ProductAdminV1Controller 참고.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductService productService;
    private final LikeService likeService;

    /** FR-P-02. 상품 상세 조회 */
    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductInfo info = productService.getById(productId);
        long likeCount = likeService.countByProductId(productId);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info, likeCount));
    }

    /** FR-P-01. 상품 목록 조회 (brandId 필터, sort, 페이지네이션) */
    @GetMapping
    public ApiResponse<Page<ProductV1Dto.ProductResponse>> getAllProducts(
        @RequestParam(required = false) Long brandId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ProductInfo> products = productService.getAll(pageable, brandId);

        List<Long> productIds = products.stream().map(ProductInfo::id).toList();
        Map<Long, Long> likeCounts = likeService.countAllByProductIdIn(productIds);

        return ApiResponse.success(
            products.map(info -> ProductV1Dto.ProductResponse.from(
                info,
                likeCounts.getOrDefault(info.id(), 0L)
            ))
        );
    }
}
