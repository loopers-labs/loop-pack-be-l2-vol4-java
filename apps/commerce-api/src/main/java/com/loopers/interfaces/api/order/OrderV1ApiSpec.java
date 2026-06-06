package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Order API", description = "주문 API")
public interface OrderV1ApiSpec {

    @Operation(
        summary = "주문 생성",
        description = "로그인한 회원이 상품 목록과 수량으로 주문을 생성합니다. 같은 상품이 여러 라인으로 들어오면 수량을 합산합니다."
    )
    ApiResponse<OrderV1Dto.OrderResponse> placeOrder(LoginUser loginUser, OrderV1Dto.PlaceOrderRequest request);

    @Operation(
        summary = "내 주문 목록 조회",
        description = "로그인한 회원이 지정한 기간(from~to, 일 단위) 내 본인 주문을 최신순으로 조회합니다."
    )
    ApiResponse<List<OrderV1Dto.MyOrderSummary>> getMyOrders(
        LoginUser loginUser,
        @Parameter(description = "조회 시작일 (yyyy-MM-dd)", example = "2026-05-01") LocalDate from,
        @Parameter(description = "조회 종료일 (yyyy-MM-dd)", example = "2026-05-31") LocalDate to
    );

    @Operation(
        summary = "내 주문 상세 조회",
        description = "로그인한 회원이 본인 주문의 상세를 스냅샷 그대로 조회합니다. 타인 주문이거나 존재하지 않으면 ORDER_NOT_FOUND."
    )
    ApiResponse<OrderV1Dto.MyOrderDetail> getMyOrder(LoginUser loginUser, Long orderId);
}
