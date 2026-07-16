package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue V1 API", description = "주문 대기열 API 입니다.")
public interface QueueV1ApiSpec {

    @Operation(
            summary = "대기열 진입",
            description = "로그인 사용자를 주문 대기열에 세웁니다. 이미 대기 중이면 기존 순번을, "
                    + "이미 입장 토큰을 받았다면 토큰을 그대로 반환합니다(멱등)."
    )
    ApiResponse<QueueV1Dto.PositionResponse> enter(Long userId);

    @Operation(
            summary = "대기 순번 조회",
            description = "현재 순번과 예상 대기 시간을 반환합니다. 응답의 pollAfterMillis 이후 재조회하세요. "
                    + "입장 토큰이 발급되었다면 position=0 과 함께 토큰을 반환합니다."
    )
    ApiResponse<QueueV1Dto.PositionResponse> getPosition(Long userId);
}
