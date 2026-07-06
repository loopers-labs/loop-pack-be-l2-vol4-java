package com.loopers.application.activity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 유저 행동 로깅 리스너 — 변경 행동(좋아요/취소/주문).
 *
 * <p>{@link TransactionPhase#AFTER_COMMIT} — 커밋되어 실제로 일어난 행동만 기록한다(롤백 시 미기록).
 * 로깅만 하므로 DB 트랜잭션은 필요 없다. 여러 유스케이스가 발행하는 행동 로깅을 이 한 곳에 모아,
 * 로깅 정책 변경 시 비즈니스 코드를 건드리지 않는다.
 */
@Slf4j
@Component
public class UserActionLogListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserAction(UserActionEvent event) {
        log.info("[UserAction] userId={}, action={}, targetId={}",
            event.userId(), event.actionType(), event.targetId());
    }
}
