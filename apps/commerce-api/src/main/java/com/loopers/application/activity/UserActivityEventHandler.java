package com.loopers.application.activity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserActivityEventHandler {

    /**
     * fallbackExecution=true: 좋아요/주문은 트랜잭션 안에서 발행되지만, 상품 조회/목록 브라우즈는
     * 트랜잭션 없는 컨트롤러에서 발행된다. AFTER_COMMIT 리스너는 활성 TX 가 없으면 기본적으로 '조용히 드롭'되므로,
     * TX 밖 발행도 실행되도록 fallback 을 켠다. (로깅만 하므로 새 TX 불필요.)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(UserActivityEvent event) {
        log.info("user activity: userId={}, type={}, targetId={}",
            event.userId(), event.type(), event.targetId());
    }
}
