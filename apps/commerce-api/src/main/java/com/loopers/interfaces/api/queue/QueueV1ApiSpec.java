package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Queue V1 API", description = "Loopers 대기열 API 입니다.")
public interface QueueV1ApiSpec {

    @Operation(summary = "대기열 진입", description = "대기열에 진입해 순번을 부여받는다. 이미 대기 중이면 순번이 현재 시각 기준으로 갱신된다.")
    ApiResponse<QueueV1Dto.EnterResponse> enter(
            @RequestHeader String loginId,
            @RequestHeader String loginPw
    );

    @Operation(summary = "대기 순번 조회", description = "현재 대기 순번과 예상 대기 시간을 조회한다. 입장 순서가 되면 응답에 입장 토큰이 포함된다.")
    ApiResponse<QueueV1Dto.PositionResponse> getPosition(
            @RequestHeader String loginId,
            @RequestHeader String loginPw
    );
}
