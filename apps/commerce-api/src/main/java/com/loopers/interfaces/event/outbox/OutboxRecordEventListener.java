package com.loopers.interfaces.event.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxableEvent;
import com.loopers.domain.outbox.OutboxService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// outbox 기록: 도메인을 가리지 않는다 — OutboxableEvent를 구현하는 이벤트라면 무엇이든 여기서 처리한다.
// 발행 도메인의 트랜잭션(BEFORE_COMMIT)에 합류한다 — 기록이 실패하면 원본 트랜잭션도 롤백된다(원자적, 유실 차단).
// 필드 매핑(OutboxModel 조립)은 OutboxModel이 알고(from), 직렬화 포맷은 여기서 결정한다.
@RequiredArgsConstructor
@Component
public class OutboxRecordEventListener {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(OutboxableEvent event) {
        outboxService.record(OutboxModel.from(event, serialize(event)));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "outbox 이벤트 직렬화에 실패했습니다.");
        }
    }
}
