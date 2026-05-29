package com.loopers.order.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Order Admin V1 API", description = "Loopers 주문 관리(관리자) API 입니다.")
public interface OrderAdminV1ApiSpec {

    @Operation(
        summary = "전체 주문 조회",
        description = "관리자가 특정 사용자로 범위를 제한하지 않고 전체 주문을 최신순으로 조회합니다."
    )
    ApiResponse<List<OrderV1Response.Summary>> getAllOrders();
}
