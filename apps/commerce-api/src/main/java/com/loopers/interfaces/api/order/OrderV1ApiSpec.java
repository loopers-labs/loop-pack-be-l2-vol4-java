package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Tag(name = "Order V1 API", description = "주문 생성·조회 API. X-Loopers-LoginId / X-Loopers-LoginPw 헤더 필요.")
public interface OrderV1ApiSpec {

    @Operation(summary = "주문 생성", description = "상품 목록으로 주문을 생성하고 재고를 차감합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "빈 items / 재고 부족 / quantity ≤ 0",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 상품",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<OrderV1Dto.CreateOrderResponse> createOrder(
            OrderV1Dto.CreateOrderRequest request,
            @Parameter(hidden = true) String userId
    );

    @Operation(summary = "내 주문 목록 조회", description = "본인의 주문 목록을 페이지네이션으로 조회합니다. 날짜 필터(startAt/endAt) 선택 적용.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<PageResult<OrderV1Dto.OrderResponse>> getOrders(
            @Parameter(hidden = true) String userId,
            @Parameter(description = "조회 시작 날짜 (YYYY-MM-DD, 예: 2024-01-01). 당일 00:00:00 KST 이후 포함.")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
            @Parameter(description = "조회 종료 날짜 (YYYY-MM-DD, 예: 2024-01-31). 당일 23:59:59 KST 이전 포함.")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt,
            @Parameter(description = "페이지 번호 (0-based, 기본값: 0)") int page,
            @Parameter(description = "페이지 크기 (기본값: 20)") int size
    );

    @Operation(summary = "주문 단건 조회", description = "본인의 주문 상세를 조회합니다. 타인의 주문 조회 시 404를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주문 없음 또는 타인 주문 접근",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<OrderV1Dto.OrderResponse> getOrder(
            @Parameter(description = "주문 ID", required = true) String orderId,
            @Parameter(hidden = true) String userId
    );
}
