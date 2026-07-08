package com.loopers.application.queue;

import com.loopers.domain.queue.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QueueFacade {

    private final QueueService queueService;

    public QueuePositionInfo enter(String loginId) {
        long position = queueService.enter(loginId);

        return QueuePositionInfo.of(position, queueService.getWaitingCount());
    }

    public QueuePositionInfo getPosition(String loginId) {
        long position = queueService.getPosition(loginId);

        return QueuePositionInfo.of(position, queueService.getWaitingCount());
    }
}