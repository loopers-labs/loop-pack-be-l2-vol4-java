package com.loopers.interfaces.apiadmin.brand;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

@Tag(name = "Brand Admin V1 API", description = "브랜드 어드민 API")
public interface BrandAdminV1ApiSpec {

    @Operation(summary = "브랜드 목록 조회", description = "등록된 브랜드 목록을 페이지 단위로 조회합니다.")
    ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>> getList(Pageable pageable);

    @Operation(summary = "브랜드 상세 조회", description = "브랜드 ID로 브랜드 상세 정보를 조회합니다.")
    ApiResponse<BrandAdminV1Dto.BrandResponse> getBrand(Long brandId);

    @Operation(summary = "브랜드 등록", description = "새로운 브랜드를 등록합니다.")
    ApiResponse<BrandAdminV1Dto.BrandResponse> register(BrandAdminV1Dto.RegisterRequest request);

    @Operation(summary = "브랜드 정보 수정", description = "브랜드 정보를 수정합니다.")
    ApiResponse<BrandAdminV1Dto.BrandResponse> update(Long brandId, BrandAdminV1Dto.UpdateRequest request);

    @Operation(summary = "브랜드 삭제", description = "브랜드를 삭제합니다.")
    ApiResponse<Void> delete(Long brandId);
}
