package com.loopers.activity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 유저 행동 이벤트를 서버 레벨 구조적 로그로 적재한다.
 * - AFTER_COMMIT: 실제 커밋된 행동만 로깅(롤백된 행동 제외)
 * - @Async: 로깅이 본 응답을 막지 않음. 로깅 실패가 본 흐름에 영향 없음(부가 로직 분리)
 */
@Slf4j
@Component
public class UserActivityLogListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserActivityEvent event) {
        log.info("user_activity userId={} action={} target={}:{} at={}",
                event.userId(), event.action(), event.targetType(), event.targetId(), event.occurredAt());
    }
}
