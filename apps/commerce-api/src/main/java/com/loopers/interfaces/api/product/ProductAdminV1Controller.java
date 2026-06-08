package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductService;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.SortType;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller {

    private final ProductService productService;

    /** FR-PA-00. 상품 목록 조회 (어드민)
     *  sort: latest(기본) | price_asc | likes_desc
     */
    @GetMapping
    public ApiResponse<Page<ProductV1Dto.ProductResponse>> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(name = "sort", defaultValue = "latest") String sort,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        ProductSearchCondition condition = ProductSearchCondition.of(brandId, SortType.from(sort));
        return ApiResponse.success(
            productService.getAll(pageable, condition).map(ProductV1Dto.ProductResponse::from)
        );
    }

    /** FR-PA-00. 상품 상세 조회 (어드민) */
    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(productService.getById(productId)));
    }

    /** FR-PA-01. 상품 등록 */
    @PostMapping
    public ApiResponse<ProductV1Dto.ProductResponse> createProduct(
        @Valid @RequestBody ProductV1Dto.CreateProductRequest request
    ) {
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(productService.create(request.toCommand())));
    }

    /** FR-PA-02. 상품 수정 (브랜드 변경 불가) */
    @PutMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> updateProduct(
        @PathVariable Long productId,
        @Valid @RequestBody ProductV1Dto.UpdateProductRequest request
    ) {
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(productService.update(productId, request.toCommand())));
    }

    /** FR-PA-03. 상품 삭제 (소프트 딜리트) */
    @DeleteMapping("/{productId}")
    public ApiResponse<Object> deleteProduct(@PathVariable Long productId) {
        productService.delete(productId);
        return ApiResponse.success();
    }
}
