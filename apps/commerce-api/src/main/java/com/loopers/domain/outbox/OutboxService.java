package com.loopers.domain.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OutboxService {

    private final OutboxRepository outboxRepository;

    public OutboxModel record(OutboxModel outbox) {
        return outboxRepository.save(outbox);
    }

    public List<OutboxModel> findPending() {
        return outboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING);
    }

    // 호출자(핸들러)의 트랜잭션에 합류하여 후속 반영 작업과 원자적으로 커밋된다.
    // true: 이 인스턴스가 처리 권한 획득, false: 이미 다른 인스턴스가 처리 완료
    @Transactional
    public boolean markDoneIfPending(String eventId) {
        return outboxRepository.markDoneIfPending(eventId);
    }
}
