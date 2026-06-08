package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Order V1 API", description = "주문 API")
public interface OrderV1ApiSpec {

    @Operation(summary = "주문 요청", description = "상품을 주문합니다.")
    ApiResponse<OrderV1Dto.OrderResponse> createOrder(OrderV1Dto.OrderRequest request, Long userId);

    @Operation(summary = "주문 목록 조회", description = "유저의 주문 목록을 날짜 범위로 조회합니다.")
    ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(Long userId, LocalDate startAt, LocalDate endAt);

    @Operation(summary = "주문 상세 조회", description = "단일 주문 상세 정보를 조회합니다.")
    ApiResponse<OrderV1Dto.OrderResponse> getOrder(Long orderId, Long userId);

    @Operation(summary = "주문 취소", description = "주문을 취소합니다.")
    ApiResponse<OrderV1Dto.OrderResponse> cancelOrder(Long orderId, Long userId);
}
