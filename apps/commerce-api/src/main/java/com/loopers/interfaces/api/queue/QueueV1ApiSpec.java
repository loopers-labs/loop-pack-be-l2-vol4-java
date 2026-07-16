package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue API", description = "주문 대기열 API")
public interface QueueV1ApiSpec {

    @Operation(
        summary = "대기열 진입",
        description = "로그인한 회원을 주문 대기열에 진입시키고 현재 순번(0-based)과 전체 대기 인원을 반환합니다. 이미 대기 중이면 기존 순번을 유지합니다."
    )
    ApiResponse<QueueV1Dto.EnterResponse> enter(LoginUser loginUser);

    @Operation(
        summary = "순번 조회",
        description = "로그인한 회원의 현재 대기 순번(0-based)과 전체 대기 인원을 반환합니다."
    )
    ApiResponse<QueueV1Dto.PositionResponse> getPosition(LoginUser loginUser);
}
