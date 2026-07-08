package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueuePositionInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queue")
public class QueueV1Controller {

    private final QueueFacade queueFacade;

    /** 대기열 진입. 유저를 대기열에 넣고(재진입이면 최초 순번 유지) 현재 순번과 전체 대기 인원을 응답한다. */
    @PostMapping("/entries")
    public ApiResponse<QueueV1Dto.PositionResponse> enter(@LoginUser String loginId) {
        QueuePositionInfo info = queueFacade.enter(loginId);

        return ApiResponse.success(QueueV1Dto.PositionResponse.from(info));
    }

    /** 순번 조회(Polling). 대기 중 유저가 자신의 현재 순번과 전체 대기 인원을 반복 조회한다. */
    @GetMapping("/position")
    public ApiResponse<QueueV1Dto.PositionResponse> position(@LoginUser String loginId) {
        QueuePositionInfo info = queueFacade.getPosition(loginId);

        return ApiResponse.success(QueueV1Dto.PositionResponse.from(info));
    }
}
