package com.loopers.queue.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.queue.application.QueueDevService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 데모/개발용. 대기열 앞에 더미 대기자를 채운다. 운영(prd)에서는 로드되지 않는다. */
@Profile("!prd")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue/dev")
public class QueueDevV1Controller {

    private final QueueDevService queueDevService;

    @PostMapping("/fill")
    public ApiResponse<Long> fill(@RequestParam(defaultValue = "500") int count) {
        return ApiResponse.success(queueDevService.fill(count));
    }

    /** 인증 유저에게 입장 토큰을 즉시 발급한다(부하테스트에서 order API 순수 측정용). */
    @PostMapping("/token")
    public ApiResponse<String> token(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(queueDevService.issueToken(String.valueOf(userId)));
    }
}
