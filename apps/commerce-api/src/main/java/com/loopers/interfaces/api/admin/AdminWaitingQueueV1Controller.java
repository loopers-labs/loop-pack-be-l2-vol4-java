package com.loopers.interfaces.api.admin;

import com.loopers.application.waitingqueue.WaitingQueueFacade;
import com.loopers.domain.waitingqueue.WaitingQueueStatusView;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 대기열 현황 API(FR-7·D8). 배치 크기·주기 튜닝을 위한 관측용.
 * 권한 체계 미적용 — 운영 시 운영자 인가 선행 필요(다른 Admin API와 동일).
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/waiting-queue")
public class AdminWaitingQueueV1Controller {

    private final WaitingQueueFacade waitingQueueFacade;

    @GetMapping("/status")
    public ApiResponse<AdminWaitingQueueV1Dto.StatusResponse> status() {
        WaitingQueueStatusView status = waitingQueueFacade.status();
        return ApiResponse.success(AdminWaitingQueueV1Dto.StatusResponse.from(status));
    }
}
