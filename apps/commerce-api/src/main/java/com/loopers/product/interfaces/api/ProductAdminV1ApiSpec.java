package com.loopers.product.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;

@Tag(name = "Product Admin V1 API", description = "Loopers 상품 관리자 API 입니다. (X-Loopers-Admin-Id 헤더 필요)")
public interface ProductAdminV1ApiSpec {

    @Operation(
        summary = "상품 등록",
        description = "브랜드, 이름, 설명, 가격, 썸네일, 초기 재고로 새 상품을 등록합니다. 초기 상태는 ON_SALE 입니다."
    )
    ApiResponse<ProductAdminV1Response.AdminDetail> create(@Valid ProductAdminV1Request.Create request);

    @Operation(
        summary = "상품 관리 단건 조회",
        description = "productId 로 상품 정보를 재고와 함께 조회합니다. (상태 무관)"
    )
    ApiResponse<ProductAdminV1Response.AdminDetail> get(Long productId);

    @Operation(
        summary = "상품 관리 목록 조회",
        description = "전체 상품 목록을 재고와 함께 반환합니다. (상태 무관)"
    )
    ApiResponse<List<ProductAdminV1Response.AdminDetail>> getAll();

    @Operation(
        summary = "상품 수정",
        description = "productId 의 이름, 설명, 가격, 썸네일을 변경합니다. 브랜드는 변경되지 않습니다."
    )
    ApiResponse<ProductAdminV1Response.AdminDetail> update(Long productId, @Valid ProductAdminV1Request.Update request);

    @Operation(
        summary = "상품 삭제",
        description = "productId 의 상품과 재고를 soft delete 합니다."
    )
    ApiResponse<Void> delete(Long productId);

    @Operation(
        summary = "판매 중지",
        description = "productId 의 상품을 SUSPENDED 상태로 전환합니다."
    )
    ApiResponse<Void> suspend(Long productId);

    @Operation(
        summary = "판매 재개",
        description = "productId 의 상품을 ON_SALE 상태로 전환합니다."
    )
    ApiResponse<Void> resume(Long productId);
}
