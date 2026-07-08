package com.loopers.interfaces.api.queue;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order Queue V1 API", description = "Loopers 주문 대기열 API 입니다.")
public interface OrderQueueV1ApiSpec {

    @Operation(
        summary = "대기열 진입",
        description = "주문 대기열에 진입하고 현재 순번을 반환합니다. 이미 진입한 유저는 기존 순번을 유지합니다.",
        parameters = {
            @Parameter(name = "X-Loopers-LoginId", in = ParameterIn.HEADER, required = true, description = "로그인 ID"),
            @Parameter(name = "X-Loopers-LoginPw", in = ParameterIn.HEADER, required = true, description = "비밀번호")
        }
    )
    ApiResponse<OrderQueueV1Dto.QueuePositionResponse> enter(@Parameter(hidden = true) User user);

    @Operation(
        summary = "순번 조회",
        description = "대기열에서 현재 순번을 조회합니다. 클라이언트가 폴링으로 반복 호출합니다.",
        parameters = {
            @Parameter(name = "X-Loopers-LoginId", in = ParameterIn.HEADER, required = true, description = "로그인 ID"),
            @Parameter(name = "X-Loopers-LoginPw", in = ParameterIn.HEADER, required = true, description = "비밀번호")
        }
    )
    ApiResponse<OrderQueueV1Dto.QueuePositionResponse> position(@Parameter(hidden = true) User user);
}
