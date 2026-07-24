package com.loopers.application.queue;

import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QueueFacade {

    private final UserService userService;
    private final WaitingQueueService waitingQueueService;

    public QueueInfo enter(String loginId, String loginPw) {
        UserModel user = userService.getUser(loginId, loginPw);
        return QueueInfo.from(waitingQueueService.enter(user.getId()));
    }

    public QueueInfo getPosition(String loginId, String loginPw) {
        UserModel user = userService.getUser(loginId, loginPw);
        return QueueInfo.from(waitingQueueService.getPosition(user.getId()));
    }
}
