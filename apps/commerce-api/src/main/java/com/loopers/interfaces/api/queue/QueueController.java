package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueueInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueController {

    private final QueueFacade queueFacade;

    @PostMapping("/enter")
    public ApiResponse<QueueDto.PositionResponse> enter(@RequestAttribute("userId") Long userId) {
        QueueInfo info = queueFacade.enter(userId);
        return ApiResponse.success(QueueDto.PositionResponse.from(info));
    }

    @GetMapping("/position")
    public ApiResponse<QueueDto.PositionResponse> position(@RequestAttribute("userId") Long userId) {
        QueueInfo info = queueFacade.position(userId);
        return ApiResponse.success(QueueDto.PositionResponse.from(info));
    }
}
