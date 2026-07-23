package com.loopers.tddstudy.application.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserActionLogListener {

    private static final Logger log = LoggerFactory.getLogger(UserActionLogListener.class);

    // 커밋 후에만 기록. fallbackExecution=true → 트랜잭션 없이 발행돼도(예: 조회) 동작
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(UserActionEvent event) {
        log.info("[행동로그] userId={}, action={}, targetId={}",
                event.userId(), event.actionType(), event.targetId());
    }
}
