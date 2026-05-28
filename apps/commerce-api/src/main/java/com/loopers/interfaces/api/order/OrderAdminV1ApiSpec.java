package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Tag(name = "Order Admin V1 API", description = "주문 어드민 API")
public interface OrderAdminV1ApiSpec {

    @Operation(summary = "주문 단건 조회", description = "어드민 — 소유권 무관 단건 조회")
    ApiResponse<OrderV1Dto.AdminOrderResponse> get(UUID orderId);

    @Operation(summary = "전체 주문 목록 조회", description = "어드민 — 모든 주문 페이징 조회")
    ApiResponse<PageResponse<OrderV1Dto.AdminOrderResponse>> getList(Pageable pageable);
}
