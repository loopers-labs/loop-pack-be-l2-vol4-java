package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order Admin V1 API", description = "Loopers 주문 관리자 도메인 API 입니다.")
public interface OrderAdminV1ApiSpec {

    @Operation(
        summary = "관리자 주문 목록 조회",
        description = "관리자가 전체 회원의 주문 목록을 주문 시각 내림차순으로 페이징 조회한다. 각 항목은 주문 식별자·회원 식별자·상태·주문 시각·총 결제 금액을 포함한다."
    )
    ApiResponse<OrderAdminV1Dto.PageResponse> readOrders(int page, int size);

    @Operation(
        summary = "관리자 주문 상세 조회",
        description = "관리자가 특정 주문의 상세를 조회한다. 주문한 회원 식별자와 주문 항목 전체 스냅샷을 포함한다. 존재하지 않으면 자원을 찾을 수 없다고 응답한다."
    )
    ApiResponse<OrderAdminV1Dto.DetailResponse> readOrder(Long orderId);
}
