package com.loopers.interfaces.api.order;

import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.UUID;

@Tag(name = "Order V1 API", description = "주문 고객 API")
public interface OrderV1ApiSpec {

    @Operation(summary = "주문 생성", description = "재고 예약 후 PENDING 상태 주문 생성. Idempotency-Key 헤더 필수 — 동일 키 재요청 시 기존 주문 반환")
    ApiResponse<OrderV1Dto.OrderResponse> create(OrderV1Dto.CreateRequest request, String idempotencyKey, UserModel user);

    @Operation(summary = "주문 단건 조회", description = "본인 주문만 조회 가능")
    ApiResponse<OrderV1Dto.OrderResponse> get(UUID orderId, UserModel user);

    @Operation(summary = "주문 목록 조회", description = "본인 주문 목록, 날짜 범위 필터")
    ApiResponse<PageResponse<OrderV1Dto.OrderResponse>> getList(UUID userId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable, UserModel user);

    @Operation(summary = "주문 취소", description = "CONFIRMED 상태 주문만 취소 가능, 재고 복구")
    ApiResponse<OrderV1Dto.OrderResponse> cancel(UUID orderId, UserModel user);
}
