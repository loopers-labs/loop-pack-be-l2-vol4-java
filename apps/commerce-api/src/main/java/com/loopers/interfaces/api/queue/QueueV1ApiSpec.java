package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue V1 API", description = "주문 대기열 API 입니다.")
public interface QueueV1ApiSpec {

    @Operation(
        summary = "대기열 진입",
        description = "헤더로 식별한 유저를 주문 대기열에 세웁니다. 이미 대기 중이면 기존 순번을 그대로 반환하고(멱등), "
            + "이미 입장 토큰을 보유하면 position 0 과 토큰을 반환합니다."
    )
    ApiResponse<QueueV1Dto.QueueResponse> enter(AuthHeaders auth);

    @Operation(
        summary = "대기 순번 조회",
        description = "현재 순번·전체 대기 인원·예상 대기 시간·권장 폴링 간격을 반환합니다. "
            + "입장 토큰이 발급되었으면 position 0 과 토큰을 반환하고, 대기열에 없으면 404 를 반환합니다."
    )
    ApiResponse<QueueV1Dto.QueueResponse> getPosition(AuthHeaders auth);
}
