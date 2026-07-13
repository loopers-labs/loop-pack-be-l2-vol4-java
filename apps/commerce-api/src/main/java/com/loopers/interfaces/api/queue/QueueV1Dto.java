package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.QueueStatusInfo;
import jakarta.validation.constraints.NotNull;

public class QueueV1Dto {

    public record StatusResponse(String status, Long position, Long waitingCount, Long pollIntervalMillis) {
        public static StatusResponse from(QueueStatusInfo info) {
            return new StatusResponse(info.status(), info.position(), info.waitingCount(), info.pollIntervalMillis());
        }
    }

    public record LeaveRequest(
        @NotNull(message = "사용자 ID는 필수입니다.") Long userId
    ) {}
}
