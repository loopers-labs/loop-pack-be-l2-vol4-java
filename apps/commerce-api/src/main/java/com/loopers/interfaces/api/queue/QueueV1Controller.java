package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueApplicationService;
import com.loopers.application.queue.QueueInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueApplicationService queueApplicationService;

    @PostMapping("/enter")
    @Override
    public ApiResponse<QueueV1Dto.PositionResponse> enter(@LoginUser Long userId) {
        QueueInfo.Position info = queueApplicationService.enter(userId);
        return ApiResponse.success(QueueV1Dto.PositionResponse.from(info));
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<QueueV1Dto.PositionResponse> getPosition(@LoginUser Long userId) {
        QueueInfo.Position info = queueApplicationService.getPosition(userId);
        return ApiResponse.success(QueueV1Dto.PositionResponse.from(info));
    }
}
