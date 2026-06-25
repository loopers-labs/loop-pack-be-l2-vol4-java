package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;

@Tag(name = "Payment V1 API", description = "결제 요청·콜백·조회 API. (콜백 제외) X-Loopers-LoginId / X-Loopers-LoginPw 헤더 필요.")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청",
            description = "주문에 대한 PG 카드결제를 요청한다. PG 콜백을 최대 timeout 까지 대기(Long Polling)하며, "
                    + "확정되면 결과를, 미확정이면 PENDING 상태를 반환한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "결제 요청 접수/확정"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "결제 가능한 주문 상태 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주문 없음 또는 타인 주문 접근",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 진행 중/완료된 결제 존재",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    CompletableFuture<ApiResponse<PaymentV1Dto.Response>> pay(
            PaymentV1Dto.PaymentRequest request,
            @Parameter(hidden = true) String userId
    );

    @Operation(summary = "결제 콜백 (PG 전용)",
            description = "PG 시뮬레이터가 결제 확정 결과를 통보하는 엔드포인트. 사용자 인증 헤더가 필요 없다.")
    @SecurityRequirements
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "콜백 수신"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "결제 정보 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<Void> callback(PaymentV1Dto.CallbackRequest request);

    @Operation(summary = "결제 상태 조회",
            description = "본인 결제의 상태를 조회한다. PENDING 이면 PG 에 1회 조회(Poll)해 확정 시도 후 반환한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "결제 없음 또는 타인 결제 접근",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<PaymentV1Dto.Response> getPayment(
            @Parameter(description = "결제 ID", required = true) String paymentId,
            @Parameter(hidden = true) String userId
    );
}
