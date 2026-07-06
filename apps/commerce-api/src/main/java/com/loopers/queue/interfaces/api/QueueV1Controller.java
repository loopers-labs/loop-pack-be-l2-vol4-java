package com.loopers.queue.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.queue.application.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueService queueService;

    @PostMapping("/enter")
    @Override
    public ApiResponse<QueueV1Response.Enter> enter(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(QueueV1Response.Enter.from(queueService.enter(String.valueOf(userId))));
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<QueueV1Response.Position> position(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(QueueV1Response.Position.from(queueService.position(String.valueOf(userId))));
    }
}
