package com.loopers.interfaces.apiadmin.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

@Tag(name = "Product Admin V1 API", description = "상품 어드민 API")
public interface ProductAdminV1ApiSpec {

    @Operation(summary = "상품 목록 조회", description = "등록된 상품 목록을 페이지 단위로 조회합니다.")
    ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>> getProducts(Long brandId, Pageable pageable);

    @Operation(summary = "상품 상세 조회", description = "상품 ID로 상품 상세 정보를 조회합니다.")
    ApiResponse<ProductAdminV1Dto.ProductResponse> getProduct(Long productId);

    @Operation(summary = "상품 등록", description = "새로운 상품을 등록합니다.")
    ApiResponse<ProductAdminV1Dto.ProductResponse> register(ProductAdminV1Dto.RegisterRequest request);

    @Operation(summary = "상품 정보 수정", description = "상품 정보를 수정합니다.")
    ApiResponse<ProductAdminV1Dto.ProductResponse> update(Long productId, ProductAdminV1Dto.UpdateRequest request);

    @Operation(summary = "상품 삭제", description = "상품을 삭제합니다.")
    ApiResponse<Void> delete(Long productId);
}
