package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand Admin V1 API", description = "Loopers 브랜드 어드민 API 입니다.")
public interface BrandAdminV1ApiSpec {

    @Operation(
            summary = "브랜드 목록 조회",
            description = "등록된 브랜드를 페이징하여 반환합니다."
    )
    ApiResponse<BrandV1Dto.PageResponse> getBrands(String adminLdap, int page, int size);

    @Operation(
            summary = "브랜드 단건 조회",
            description = "브랜드 식별자(brandId)로 브랜드 정보를 반환합니다."
    )
    ApiResponse<BrandV1Dto.BrandResponse> getBrand(String adminLdap, Long brandId);

    @Operation(
            summary = "브랜드 등록",
            description = "이름·설명을 갖춘 브랜드를 등록합니다."
    )
    ApiResponse<BrandV1Dto.BrandResponse> register(String adminLdap, BrandV1Dto.RegisterRequest request);

    @Operation(
            summary = "브랜드 수정",
            description = "브랜드의 이름·설명을 수정합니다."
    )
    ApiResponse<Void> modify(String adminLdap, Long brandId, BrandV1Dto.ModifyRequest request);

    @Operation(
            summary = "브랜드 삭제",
            description = "브랜드를 논리 삭제합니다."
    )
    ApiResponse<Void> delete(String adminLdap, Long brandId);
}
