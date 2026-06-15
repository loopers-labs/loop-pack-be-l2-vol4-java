package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order Admin V1 API", description = "Loopers 주문 어드민 API 입니다.")
public interface OrderAdminV1ApiSpec {

    @Operation(
            summary = "전체 주문 목록 조회",
            description = "전체 사용자의 주문을 페이징하여 반환합니다."
    )
    ApiResponse<OrderV1Dto.PageResponse> getAllOrders(String adminLdap, int page, int size);

    @Operation(
            summary = "주문 단건 조회",
            description = "주문 식별자(orderId)로 주문 상세를 반환합니다."
    )
    ApiResponse<OrderV1Dto.DetailResponse> getOrder(String adminLdap, Long orderId);
}
