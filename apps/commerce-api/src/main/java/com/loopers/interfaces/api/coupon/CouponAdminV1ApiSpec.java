package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Tag(name = "Coupon Admin V1 API", description = "쿠폰 템플릿 어드민 API (삭제된 템플릿 포함)")
public interface CouponAdminV1ApiSpec {

    @Operation(summary = "쿠폰 템플릿 생성", description = "정액(FIXED)/정률(RATE) 타입 지정")
    ApiResponse<CouponV1Dto.TemplateResponse> create(@Valid CouponV1Dto.CreateRequest request);

    @Operation(summary = "쿠폰 템플릿 단건 조회", description = "어드민용 — 소프트 딜리트된 템플릿도 포함합니다.")
    ApiResponse<CouponV1Dto.TemplateResponse> get(UUID id);

    @Operation(summary = "쿠폰 템플릿 목록 조회", description = "어드민용 — 소프트 딜리트된 템플릿도 포함합니다.")
    ApiResponse<PageResponse<CouponV1Dto.TemplateResponse>> getList(Pageable pageable);

    @Operation(summary = "쿠폰 템플릿 수정")
    ApiResponse<CouponV1Dto.TemplateResponse> update(UUID id, @Valid CouponV1Dto.UpdateRequest request);

    @Operation(summary = "쿠폰 템플릿 삭제", description = "소프트 딜리트 — DB 행은 보존됩니다.")
    ApiResponse<Void> delete(UUID id);

    @Operation(summary = "발급 내역 조회", description = "특정 템플릿으로 발급된 쿠폰 목록을 페이징해 반환합니다.")
    ApiResponse<PageResponse<CouponV1Dto.UserCouponResponse>> getIssues(UUID id, Pageable pageable);
}
