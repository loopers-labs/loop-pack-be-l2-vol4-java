package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Tag(name = "Brand Admin V1 API", description = "브랜드 어드민 API (삭제된 브랜드 포함)")
public interface BrandAdminV1ApiSpec {

    @Operation(summary = "브랜드 생성")
    ApiResponse<BrandV1Dto.BrandResponse> create(@Valid BrandV1Dto.CreateRequest request);

    @Operation(summary = "브랜드 단건 조회", description = "어드민용 — 소프트 딜리트된 브랜드도 포함합니다.")
    ApiResponse<BrandV1Dto.BrandResponse> get(UUID id);

    @Operation(summary = "브랜드 목록 조회", description = "어드민용 — 소프트 딜리트된 브랜드도 포함합니다.")
    ApiResponse<Page<BrandV1Dto.BrandResponse>> getList(Pageable pageable);

    @Operation(summary = "브랜드 수정")
    ApiResponse<BrandV1Dto.BrandResponse> update(UUID id, @Valid BrandV1Dto.UpdateRequest request);

    @Operation(summary = "브랜드 삭제", description = "소프트 딜리트 — DB 행은 보존됩니다.")
    ApiResponse<Void> delete(UUID id);
}
