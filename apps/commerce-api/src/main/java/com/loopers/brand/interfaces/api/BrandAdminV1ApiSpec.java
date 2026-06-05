package com.loopers.brand.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;

@Tag(name = "Brand Admin V1 API", description = "Loopers 브랜드 관리자 API 입니다. (X-Loopers-Admin-Id 헤더 필요)")
public interface BrandAdminV1ApiSpec {

    @Operation(
        summary = "브랜드 등록",
        description = "이름, 설명, 로고로 새 브랜드를 등록합니다."
    )
    ApiResponse<BrandV1Response.Detail> create(@Valid BrandAdminV1Request.Create request);

    @Operation(
        summary = "브랜드 관리 단건 조회",
        description = "brandId 로 브랜드 정보를 조회합니다."
    )
    ApiResponse<BrandV1Response.Detail> get(Long brandId);

    @Operation(
        summary = "브랜드 관리 목록 조회",
        description = "등록된 브랜드 목록을 반환합니다."
    )
    ApiResponse<List<BrandV1Response.Detail>> getAll();

    @Operation(
        summary = "브랜드 수정",
        description = "brandId 의 브랜드 이름, 설명, 로고를 변경합니다."
    )
    ApiResponse<BrandV1Response.Detail> update(Long brandId, @Valid BrandAdminV1Request.Update request);

    @Operation(
        summary = "브랜드 삭제",
        description = "brandId 의 브랜드를 soft delete 합니다."
    )
    ApiResponse<Void> delete(Long brandId);
}
