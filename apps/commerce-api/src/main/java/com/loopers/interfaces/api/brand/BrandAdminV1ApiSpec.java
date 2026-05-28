package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand Admin API", description = "어드민 브랜드 API")
public interface BrandAdminV1ApiSpec {

    @Operation(
        summary = "어드민 브랜드 등록",
        description = "새 브랜드를 등록합니다. 이름이 이미 존재하면 409 DUPLICATE_BRAND_NAME 을 반환합니다."
    )
    ApiResponse<BrandAdminV1Dto.Response> create(BrandAdminV1Dto.CreateRequest request);

    @Operation(
        summary = "어드민 브랜드 목록 조회",
        description = "삭제된 브랜드를 포함한 전체 브랜드를 페이지 단위로 조회합니다. " +
            "기본 page=0, size=20. 응답의 각 항목에는 deletedAt 이 포함되어 삭제 여부를 식별할 수 있습니다."
    )
    ApiResponse<BrandAdminV1Dto.PageResponse> list(int page, int size);

    @Operation(
        summary = "어드민 브랜드 단건 조회",
        description = "삭제된 브랜드도 조회할 수 있습니다. 응답에는 deletedAt 이 포함됩니다."
    )
    ApiResponse<BrandAdminV1Dto.Response> getBrand(Long brandId);

    @Operation(
        summary = "어드민 브랜드 수정 (PATCH)",
        description = "전달된 필드만 변경합니다. null 필드는 변경하지 않습니다. " +
            "삭제된 브랜드는 404 BRAND_NOT_FOUND, 다른 브랜드와 이름이 중복되면 409 DUPLICATE_BRAND_NAME 을 반환합니다."
    )
    ApiResponse<BrandAdminV1Dto.Response> update(Long brandId, BrandAdminV1Dto.UpdateRequest request);

    @Operation(
        summary = "어드민 브랜드 삭제",
        description = "브랜드와 소속 상품을 모두 soft-delete 합니다. " +
            "이미 삭제된 브랜드를 다시 삭제하면 멱등하게 성공합니다. " +
            "존재하지 않는 브랜드는 404 BRAND_NOT_FOUND 를 반환합니다."
    )
    ApiResponse<Void> delete(Long brandId);
}
