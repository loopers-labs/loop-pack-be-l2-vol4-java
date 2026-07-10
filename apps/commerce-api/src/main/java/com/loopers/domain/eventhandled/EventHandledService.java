package com.loopers.domain.eventhandled;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class EventHandledService {

    private final EventHandledRepository eventHandledRepository;

    // 호출자(컨슈머 처리 트랜잭션)에 합류하여 후속 반영 작업과 원자적으로 커밋된다.
    @Transactional
    public boolean markHandled(String eventId) {
        return eventHandledRepository.markHandled(eventId);
    }
}
