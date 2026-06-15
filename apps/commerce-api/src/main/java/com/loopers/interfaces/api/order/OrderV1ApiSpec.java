package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;

@Tag(name = "Order V1 API", description = "Loopers 주문 대고객 API 입니다.")
public interface OrderV1ApiSpec {

    @Operation(
            summary = "주문 생성",
            description = "로그인 사용자가 상품 목록으로 주문을 생성합니다."
    )
    ApiResponse<OrderV1Dto.CreatedResponse> place(Long userId, OrderV1Dto.PlaceRequest request);

    @Operation(
            summary = "내 주문 목록 조회",
            description = "로그인 사용자의 주문을 기간(startAt~endAt)·페이지 조건으로 페이징하여 반환합니다."
    )
    ApiResponse<OrderV1Dto.PageResponse> getMyOrders(Long userId, LocalDate startAt, LocalDate endAt, int page, int size);

    @Operation(
            summary = "내 주문 단건 조회",
            description = "로그인 사용자의 주문 상세를 반환합니다. 본인 주문이 아니면 조회할 수 없습니다."
    )
    ApiResponse<OrderV1Dto.DetailResponse> getMyOrder(Long userId, Long orderId);
}
