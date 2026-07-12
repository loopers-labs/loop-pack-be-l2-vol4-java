package com.loopers.interfaces.api.ordering.queue;

import com.loopers.application.ordering.queue.OrderQueueService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.HeaderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class OrderQueueController {

    private final OrderQueueService orderQueueService;

    @PostMapping("/enter")
    public ApiResponse<OrderQueueDto.OrderQueueResponse> enter(
        @RequestHeader(HeaderValidator.LOGIN_ID) String loginId,
        @RequestHeader(HeaderValidator.LOGIN_PW) String loginPw
    ) {
        HeaderValidator.validateUser(loginId, loginPw);
        return ApiResponse.success(OrderQueueDto.OrderQueueResponse.from(orderQueueService.enter(loginId)));
    }

    @GetMapping("/position")
    public ApiResponse<OrderQueueDto.OrderQueueResponse> getPosition(
        @RequestHeader(HeaderValidator.LOGIN_ID) String loginId,
        @RequestHeader(HeaderValidator.LOGIN_PW) String loginPw
    ) {
        HeaderValidator.validateUser(loginId, loginPw);
        return ApiResponse.success(OrderQueueDto.OrderQueueResponse.from(orderQueueService.getPosition(loginId)));
    }
}
