package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;
import com.loopers.application.queue.QueuePositionInfo;
import com.loopers.application.queue.WaitingQueueFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller implements QueueV1ApiSpec {

    private final WaitingQueueFacade waitingQueueFacade;

    @PostMapping("/enter")
    @Override
    public ApiResponse<QueueV1Dto.EnterResponse> enter(@AuthenticatedUser LoginUser loginUser) {
        QueueInfo info = waitingQueueFacade.enter(loginUser.id());
        return ApiResponse.success(QueueV1Dto.EnterResponse.from(info));
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<QueueV1Dto.PositionResponse> getPosition(@AuthenticatedUser LoginUser loginUser) {
        QueuePositionInfo info = waitingQueueFacade.getPosition(loginUser.id());
        return ApiResponse.success(QueueV1Dto.PositionResponse.from(info));
    }
}
