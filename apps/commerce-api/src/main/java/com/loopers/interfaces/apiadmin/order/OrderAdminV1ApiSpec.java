package com.loopers.interfaces.apiadmin.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

@Tag(name = "Order Admin V1 API", description = "주문 어드민 API")
public interface OrderAdminV1ApiSpec {

    @Operation(summary = "주문 목록 조회", description = "전체 주문 목록을 페이지 단위로 조회합니다.")
    ApiResponse<PageResponse<OrderAdminV1Dto.OrderResponse>> getOrders(Pageable pageable);

    @Operation(summary = "주문 상세 조회", description = "주문 ID로 단일 주문 상세 정보를 조회합니다.")
    ApiResponse<OrderAdminV1Dto.OrderResponse> getOrder(Long orderId);
}