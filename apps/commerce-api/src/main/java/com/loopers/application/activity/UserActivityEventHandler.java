package com.loopers.application.activity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserActivityEventHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserActivityEvent event) {
        log.info("user activity: userId={}, type={}, targetId={}",
            event.userId(), event.type(), event.targetId());
    }
}
