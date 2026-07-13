package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueApplicationService;
import com.loopers.domain.queue.QueueStatusInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queues")
public class QueueV1Controller {

    private final QueueApplicationService queueApplicationService;

    @PostMapping("/{productId}")
    public ApiResponse<QueueV1Dto.StatusResponse> enter(
        @QueueUser Long userId,
        @PathVariable Long productId
    ) {
        QueueStatusInfo info = queueApplicationService.enter(productId, userId);
        return ApiResponse.success(QueueV1Dto.StatusResponse.from(info));
    }

    @GetMapping("/{productId}")
    public ApiResponse<QueueV1Dto.StatusResponse> status(
        @QueueUser Long userId,
        @PathVariable Long productId
    ) {
        QueueStatusInfo info = queueApplicationService.status(productId, userId);
        return ApiResponse.success(QueueV1Dto.StatusResponse.from(info));
    }

    /**
     * 브라우저 이탈 신호 — navigator.sendBeacon() 으로 호출된다.
     *
     * <p>sendBeacon은 POST만 가능하고 커스텀 헤더를 설정할 수 없어 이 저장소의 인증 헤더를
     * 실어 보낼 수 없다. 그래서 이 엔드포인트는 의도적으로 AuthUser를 쓰지 않고 요청 바디로
     * userId를 직접 받는다 — 인증 누락이 아니라 sendBeacon의 제약에 따른 설계이니 "고치지" 말 것.
     */
    @PostMapping("/{productId}/leave")
    public ApiResponse<Object> leave(
        @PathVariable Long productId,
        @Valid @RequestBody QueueV1Dto.LeaveRequest request
    ) {
        queueApplicationService.leave(productId, request.userId());
        return ApiResponse.success(null);
    }
}
