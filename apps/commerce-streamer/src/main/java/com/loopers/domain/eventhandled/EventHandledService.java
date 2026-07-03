package com.loopers.domain.eventhandled;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class EventHandledService {

    private final EventHandledRepository repository;

    @Transactional(readOnly = true)
    public boolean isHandled(String eventId, String consumerGroup) {
        return repository.existsByEventIdAndConsumerGroup(eventId, consumerGroup);
    }

    /**
     * 처리 완료 기록. UNIQUE 위반이 나면 다른 consumer 인스턴스가 먼저 처리했다는 의미 — 무시.
     */
    @Transactional
    public void markHandled(String eventId, String consumerGroup, String eventType) {
        try {
            repository.save(new EventHandledRecord(eventId, consumerGroup, eventType, ZonedDateTime.now()));
        } catch (DataIntegrityViolationException race) {
            // 동시 커밋으로 UNIQUE 위반 — 이미 처리됐다는 신호. 통과.
        }
    }
}
