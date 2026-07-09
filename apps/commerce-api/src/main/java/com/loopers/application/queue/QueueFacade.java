package com.loopers.application.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final QueueService queueService;

    public QueueInfo enter(Long userId) {
        return queueService.enter(userId);
    }

    public QueueInfo position(Long userId) {
        return queueService.position(userId);
    }

    public boolean validateToken(Long userId, String token) {
        return queueService.validateToken(userId, token);
    }

    public void completeOrder(Long userId) {
        queueService.deleteToken(userId);
    }
}
