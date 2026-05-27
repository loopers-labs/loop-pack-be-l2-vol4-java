package com.loopers.brand.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;

@Tag(name = "Brand V1 API", description = "Loopers 브랜드 API 입니다.")
public interface BrandV1ApiSpec {

    @Operation(
        summary = "브랜드 등록",
        description = "이름과 설명으로 새 브랜드를 등록합니다."
    )
    ApiResponse<BrandV1Dto.BrandResponse> create(@Valid BrandV1Dto.CreateRequest request);

    @Operation(
        summary = "브랜드 단건 조회",
        description = "brandId 로 브랜드 정보를 조회합니다."
    )
    ApiResponse<BrandV1Dto.BrandResponse> get(Long brandId);

    @Operation(
        summary = "브랜드 목록 조회",
        description = "활성 상태인 브랜드 목록을 반환합니다."
    )
    ApiResponse<List<BrandV1Dto.BrandResponse>> getAll();

    @Operation(
        summary = "브랜드 수정",
        description = "brandId 의 브랜드 이름과 설명을 변경합니다."
    )
    ApiResponse<BrandV1Dto.BrandResponse> update(Long brandId, @Valid BrandV1Dto.UpdateRequest request);

    @Operation(
        summary = "브랜드 삭제",
        description = "brandId 의 브랜드를 soft delete 합니다."
    )
    ApiResponse<Void> delete(Long brandId);
}
