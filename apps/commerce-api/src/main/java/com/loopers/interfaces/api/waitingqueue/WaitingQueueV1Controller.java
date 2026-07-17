package com.loopers.interfaces.api.waitingqueue;

import com.loopers.application.user.UserFacade;
import com.loopers.application.waitingqueue.WaitingQueueFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대기열(Virtual Waiting Room) — 주문 API 앞단 관문.
 * 사용자 식별은 기존 컨벤션(X-Loopers-LoginId/LoginPw 인증)을 따른다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/waiting-queue")
public class WaitingQueueV1Controller {

    private final WaitingQueueFacade waitingQueueFacade;
    private final UserFacade userFacade;

    /** 대기열 진입(FR-1). */
    @PostMapping("/enter")
    public ApiResponse<WaitingQueueV1Dto.RankResponse> enter(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        Long userId = userFacade.authenticate(loginId, loginPw);
        return ApiResponse.success(WaitingQueueV1Dto.RankResponse.from(waitingQueueFacade.enter(userId)));
    }

    /** 순번/예상 대기 시간 조회(FR-2·FR-6, Polling 대상). */
    @GetMapping("/rank")
    public ApiResponse<WaitingQueueV1Dto.RankResponse> rank(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        Long userId = userFacade.authenticate(loginId, loginPw);
        return ApiResponse.success(WaitingQueueV1Dto.RankResponse.from(waitingQueueFacade.getRank(userId)));
    }
}
