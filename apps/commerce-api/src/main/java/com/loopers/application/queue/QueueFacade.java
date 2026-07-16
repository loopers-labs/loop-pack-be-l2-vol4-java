package com.loopers.application.queue;

import com.loopers.domain.queue.QueuePosition;
import com.loopers.domain.queue.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QueueFacade {

    private final QueueService queueService;

    public QueuePositionInfo enter(String loginId) {
        queueService.enter(loginId);

        QueuePosition status = queueService.getStatus(loginId);
        long waitingCount = queueService.getWaitingCount();

        return QueuePositionInfo.from(status, waitingCount);
    }

    public QueuePositionInfo getPosition(String loginId) {
        QueuePosition status = queueService.getStatus(loginId);
        long waitingCount = queueService.getWaitingCount();

        return QueuePositionInfo.from(status, waitingCount);
    }
}