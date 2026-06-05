package com.loopers.interfaces.api.admin.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.product.dto.ProductAdminV1Response;
import com.loopers.interfaces.api.auth.AdminUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Product Admin V1 API", description = "Loopers 상품 어드민 API 입니다. X-Loopers-Ldap 헤더로 인증합니다.")
public interface ProductAdminV1ApiSpec {

    @Operation(summary = "상품 목록 조회", description = "브랜드/정렬 조건으로 상품을 페이징하여 조회합니다(재고 포함).")
    ApiResponse<Page<ProductAdminV1Response>> search(AdminUser admin, Long brandId, String sort, Pageable pageable);

    @Operation(summary = "상품 상세 조회", description = "상품 단건을 조회합니다(재고 포함).")
    ApiResponse<ProductAdminV1Response> getProduct(AdminUser admin, Long productId);
}
