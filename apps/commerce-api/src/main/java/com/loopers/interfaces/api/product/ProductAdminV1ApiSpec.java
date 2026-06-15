package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product Admin V1 API", description = "Loopers 상품 어드민 API 입니다.")
public interface ProductAdminV1ApiSpec {

    @Operation(
            summary = "상품 목록 조회",
            description = "브랜드(brandId)·정렬(sort)·페이지 조건으로 상품을 페이징하여 반환합니다."
    )
    ApiResponse<ProductV1Dto.PageResponse> getProducts(String adminLdap, Long brandId, String sort, int page, int size);

    @Operation(
            summary = "상품 단건 조회",
            description = "상품 식별자(productId)로 상품 상세를 반환합니다."
    )
    ApiResponse<ProductV1Dto.DetailResponse> getProduct(String adminLdap, Long productId);

    @Operation(
            summary = "상품 등록",
            description = "브랜드·이름·가격·재고를 갖춘 상품을 등록합니다."
    )
    ApiResponse<ProductV1Dto.CreatedResponse> register(String adminLdap, ProductV1Dto.RegisterRequest request);

    @Operation(
            summary = "상품 수정",
            description = "상품의 이름·가격·재고를 한 번에 수정합니다."
    )
    ApiResponse<Void> modify(String adminLdap, Long productId, ProductV1Dto.ModifyRequest request);

    @Operation(
            summary = "상품 삭제",
            description = "상품을 논리 삭제합니다."
    )
    ApiResponse<Void> delete(String adminLdap, Long productId);
}
