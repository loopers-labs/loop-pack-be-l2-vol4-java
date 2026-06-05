package com.loopers.interfaces.api.admin.brand;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.brand.dto.BrandAdminV1Response;
import com.loopers.interfaces.api.auth.AdminUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Brand Admin V1 API", description = "Loopers 브랜드 어드민 API 입니다. X-Loopers-Ldap 헤더로 인증합니다.")
public interface BrandAdminV1ApiSpec {

    @Operation(summary = "브랜드 목록 조회", description = "브랜드를 페이징하여 조회합니다(상품 수 포함).")
    ApiResponse<Page<BrandAdminV1Response>> search(AdminUser admin, Pageable pageable);

    @Operation(summary = "브랜드 상세 조회", description = "브랜드 단건을 조회합니다(상품 수 포함).")
    ApiResponse<BrandAdminV1Response> getBrand(AdminUser admin, Long brandId);
}
