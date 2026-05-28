package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Tag(name = "Product Admin V1 API", description = "상품 어드민 API")
public interface ProductAdminV1ApiSpec {

    @Operation(summary = "상품 등록")
    ApiResponse<ProductV1Dto.AdminProductResponse> create(@Valid ProductV1Dto.CreateRequest request);

    @Operation(summary = "상품 단건 조회 (어드민 — 삭제된 상품 포함)")
    ApiResponse<ProductV1Dto.AdminProductResponse> get(UUID id);

    @Operation(summary = "상품 목록 조회 (어드민 — 삭제된 상품 포함)", description = "brandId 필터 지원")
    ApiResponse<PageResponse<ProductV1Dto.AdminProductResponse>> getList(UUID brandId, Pageable pageable);

    @Operation(summary = "상품 수정")
    ApiResponse<ProductV1Dto.AdminProductResponse> update(UUID id, @Valid ProductV1Dto.UpdateRequest request);

    @Operation(summary = "상품 삭제 (소프트딜리트)")
    ApiResponse<Void> delete(UUID id);
}
