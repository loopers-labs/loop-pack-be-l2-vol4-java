package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueueInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueFacade queueFacade;

    @PostMapping("/enter")
    @Override
    public ApiResponse<QueueV1Dto.QueueResponse> enter(AuthHeaders auth) {
        QueueInfo info = queueFacade.enter(auth.loginId());
        return ApiResponse.success(QueueV1Dto.QueueResponse.from(info));
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<QueueV1Dto.QueueResponse> getPosition(AuthHeaders auth) {
        QueueInfo info = queueFacade.getPosition(auth.loginId());
        return ApiResponse.success(QueueV1Dto.QueueResponse.from(info));
    }
}
