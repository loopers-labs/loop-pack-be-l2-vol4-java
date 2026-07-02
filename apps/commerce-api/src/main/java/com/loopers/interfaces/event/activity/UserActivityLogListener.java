package com.loopers.interfaces.event.activity;

import com.loopers.domain.UserActivityEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 유저 행동 로깅(C급, best-effort): 조회·목록조회는 트랜잭션이 없고 좋아요·주문은 트랜잭션이 있어
// fallbackExecution=true로 두 경우 모두 하나의 리스너가 받는다 — 트랜잭션이 있으면 커밋 후,
// 없으면 즉시 실행된다. 지금은 SLF4J 로깅뿐이며, Step 2에서 event_log 적재로 대체될 자리다.
@Slf4j
@Component
public class UserActivityLogListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void log(UserActivityEvent event) {
        log.info(
                "user activity eventId={} userId={} aggregateType={} aggregateId={} eventType={}",
                event.eventId(), event.userId(), event.aggregateType(), event.aggregateId(), event.eventType());
    }
}
