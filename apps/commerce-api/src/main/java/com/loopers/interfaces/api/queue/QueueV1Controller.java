package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller {

    private final QueueFacade queueFacade;

    /** FR-Q-01. 대기열 진입 — 이미 대기 중이면 기존 순번을 그대로 응답한다. */
    @PostMapping("/enter")
    public ApiResponse<QueueV1Dto.PositionResponse> enter(@CurrentUser UserModel currentUser) {
        return ApiResponse.success(
            QueueV1Dto.PositionResponse.from(queueFacade.enter(currentUser.getId()))
        );
    }

    /** FR-Q-02. 순번 조회 — 현재 순번과 전체 대기 인원을 응답한다. */
    @GetMapping("/position")
    public ApiResponse<QueueV1Dto.PositionResponse> getPosition(@CurrentUser UserModel currentUser) {
        return ApiResponse.success(
            QueueV1Dto.PositionResponse.from(queueFacade.getPosition(currentUser.getId()))
        );
    }
}
