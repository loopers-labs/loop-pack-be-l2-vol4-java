package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.WaitingQueueRank;
import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.infrastructure.queue.QueueProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final UserService userService;
    private final WaitingQueueService waitingQueueService;
    private final EntryTokenService entryTokenService;
    private final QueueProperties queueProperties;

    public QueueInfo enter(String loginId, String loginPw) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        WaitingQueueRank rank = waitingQueueService.enter(user.getId());
        return QueueInfo.forEnter(rank.value());
    }

    public QueueInfo getPosition(String loginId, String loginPw) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        WaitingQueueRank rank = waitingQueueService.getRank(user.getId());
        Long size = waitingQueueService.size();
        String token = entryTokenService.find(user.getId()).orElse(null);

        // 이미 발급받아 대기열(ZSET)에서 빠진 유저는 rank가 null이다 — 더 이상 기다릴 필요가 없으므로 position 0으로 취급한다.
        long position = rank != null ? rank.value() : 0L;
        long estimatedWaitSeconds = (long) Math.ceil((double) position / queueProperties.throughputPerSecond());
        return QueueInfo.forPosition(position, size, estimatedWaitSeconds, token);
    }
}
