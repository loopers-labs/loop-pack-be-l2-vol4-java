package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.OrderQueueFacade;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class OrderQueueV1Controller implements OrderQueueV1ApiSpec {

    private final OrderQueueFacade orderQueueFacade;

    @PostMapping("/enter")
    @Override
    public ApiResponse<OrderQueueV1Dto.QueuePositionResponse> enter(@LoginUser User user) {
        return ApiResponse.success(OrderQueueV1Dto.QueuePositionResponse.from(orderQueueFacade.enter(user.getId())));
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<OrderQueueV1Dto.QueuePositionResponse> position(@LoginUser User user) {
        return ApiResponse.success(OrderQueueV1Dto.QueuePositionResponse.from(orderQueueFacade.position(user.getId())));
    }
}
