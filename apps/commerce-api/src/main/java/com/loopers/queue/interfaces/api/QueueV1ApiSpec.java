package com.loopers.queue.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue V1 API", description = "주문 대기열 API 입니다.")
public interface QueueV1ApiSpec {

    @Operation(
        summary = "대기열 진입",
        description = "인증된 사용자를 주문 대기열에 넣고 순번을 돌려줍니다. 이미 대기 중이면 순번을 유지합니다."
    )
    ApiResponse<QueueV1Response.Enter> enter(@Parameter(hidden = true) Long userId);

    @Operation(
        summary = "순번 조회",
        description = "현재 순번과 예상 대기시간, 다음 폴링 간격을 조회합니다. 대기열에 없으면 404 입니다."
    )
    ApiResponse<QueueV1Response.Position> position(@Parameter(hidden = true) Long userId);
}
