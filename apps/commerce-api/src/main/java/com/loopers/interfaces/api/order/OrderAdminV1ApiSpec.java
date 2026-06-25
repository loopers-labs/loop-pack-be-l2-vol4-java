package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order Admin V1 API", description = "주문 관리 API (Admin). X-Loopers-Ldap: loopers.admin 헤더 필요.")
@SecurityRequirement(name = "X-Loopers-Ldap")
public interface OrderAdminV1ApiSpec {

    @Operation(summary = "전체 주문 목록 조회", description = "전체 사용자의 주문 목록을 페이지네이션으로 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<PageResult<OrderV1Dto.AdminOrderResponse>> getOrders(
            @Parameter(description = "페이지 번호 (0-based, 기본값: 0)") int page,
            @Parameter(description = "페이지 크기 (기본값: 20)") int size
    );

    @Operation(summary = "주문 단건 조회 (Admin)", description = "주문 ID로 주문 상세를 조회합니다. userId 포함.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주문 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<OrderV1Dto.AdminOrderResponse> getOrder(
            @Parameter(description = "주문 ID", required = true) String orderId
    );
}
