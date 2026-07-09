package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.WaitingQueueFacade;
import com.loopers.application.queue.WaitingQueueInfo;
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
public class QueueV1Controller {

    private final WaitingQueueFacade waitingQueueFacade;

    @PostMapping("/enter")
    public ApiResponse<QueueDto.Position.V1.Response> enter(@LoginUser AuthenticatedUser user) {
        WaitingQueueInfo.Position position = waitingQueueFacade.enter(user.loginId());
        return ApiResponse.success(QueueDto.Position.V1.Response.from(position));
    }

    @GetMapping("/position")
    public ApiResponse<QueueDto.Position.V1.Response> position(@LoginUser AuthenticatedUser user) {
        WaitingQueueInfo.Position position = waitingQueueFacade.position(user.loginId());
        return ApiResponse.success(QueueDto.Position.V1.Response.from(position));
    }
}
