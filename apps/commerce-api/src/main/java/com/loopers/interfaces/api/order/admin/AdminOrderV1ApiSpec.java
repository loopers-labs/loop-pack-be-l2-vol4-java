package com.loopers.interfaces.api.order.admin;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order Admin API", description = "어드민 주문 API")
public interface AdminOrderV1ApiSpec {

    @Operation(
        summary = "어드민 전체 주문 목록 조회",
        description = "전체 회원의 주문을 페이지 단위로 최신순 조회합니다. 기본 page=0, size=20. " +
            "각 항목에 주문자 식별 정보(userId, buyerLoginId)가 포함됩니다."
    )
    ApiResponse<AdminOrderV1Dto.PageResponse> getAllOrders(int page, int size);

    @Operation(
        summary = "어드민 주문 단건 조회",
        description = "임의 회원의 주문 상세를 주문자 정보와 함께 조회합니다. 존재하지 않으면 404 ORDER_NOT_FOUND."
    )
    ApiResponse<AdminOrderV1Dto.AdminOrderDetail> getOrder(Long orderId);
}
