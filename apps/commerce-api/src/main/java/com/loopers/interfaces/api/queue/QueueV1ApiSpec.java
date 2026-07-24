package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Queue", description = "주문 대기열 API")
public interface QueueV1ApiSpec {

    @Operation(summary = "대기열 진입", description = "주문 대기열에 진입하고 현재 순번을 반환한다. 이미 진입한 경우 순번이 유지된다.")
    ApiResponse<QueueV1Dto.QueueResponse> enter(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    );

    @Operation(summary = "순번 조회", description = "대기열에서 현재 순번과 전체 대기 인원을 조회한다.")
    ApiResponse<QueueV1Dto.QueueResponse> getPosition(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    );
}
