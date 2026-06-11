package com.loopers.order.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;

@Tag(name = "Order V1 API", description = "Loopers 주문 API 입니다.")
public interface OrderV1ApiSpec {

    @Operation(
        summary = "주문 요청",
        description = "인증된 사용자가 상품을 주문합니다. 재고를 확인/차감하고 주문 당시 상품·브랜드 정보를 스냅샷으로 저장하며, 주문은 PENDING 으로 생성됩니다."
    )
    ApiResponse<OrderV1Response.Detail> create(
        @Parameter(hidden = true) Long userId,
        @Valid OrderV1Request.Create request
    );

    @Operation(
        summary = "내 주문 목록 조회",
        description = "인증된 사용자가 본인의 주문 목록을 최신순으로 조회합니다."
    )
    ApiResponse<List<OrderV1Response.Summary>> getMyOrders(@Parameter(hidden = true) Long userId);
}
