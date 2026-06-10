package com.loopers.interfaces.api.order;

import java.time.LocalDate;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order V1 API", description = "Loopers 주문 도메인 API 입니다.")
public interface OrderV1ApiSpec {

    @Operation(
        summary = "주문 생성",
        description = "회원이 1개 이상의 상품 항목을 묶어 주문한다. 각 항목의 재고를 차감하고 주문 시점 상품 정보를 스냅샷으로 기록한다. "
            + "보유한 발급 쿠폰(userCouponId)을 한 장 적용하면 할인 금액을 계산해 쿠폰을 사용 완료로 전이하고, 원 주문 금액·할인 금액·최종 결제 금액을 함께 기록한다."
    )
    ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        OrderV1Dto.CreateRequest request,
        @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser
    );

    @Operation(
        summary = "본인 주문 내역 조회",
        description = "회원이 본인의 주문 내역을 날짜 범위로 조회한다. 시작일·종료일 미지정 시 오늘 기준 한 달 전부터 오늘까지를 적용하며 주문 시각 내림차순으로 페이징한다."
    )
    ApiResponse<OrderV1Dto.PageResponse> readMyOrders(
        LocalDate startAt,
        LocalDate endAt,
        int page,
        int size,
        @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser
    );

    @Operation(
        summary = "본인 주문 상세 조회",
        description = "회원이 본인의 특정 주문 상세를 조회한다. 존재하지 않거나 타인의 주문이면 자원을 찾을 수 없다고 응답한다."
    )
    ApiResponse<OrderV1Dto.OrderResponse> readMyOrder(
        Long orderId,
        @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser
    );
}
