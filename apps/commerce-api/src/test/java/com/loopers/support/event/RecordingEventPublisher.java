package com.loopers.support.event;

import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 ApplicationEventPublisher — 발행된 이벤트를 기록만 하고 리스너로 전파하지 않는다.
 * 단위 테스트에서 도메인 로직과 이벤트 발행 여부만 검증하고 싶을 때 사용.
 */
public class RecordingEventPublisher implements ApplicationEventPublisher {

    private final List<Object> events = new ArrayList<>();

    @Override
    public void publishEvent(Object event) {
        events.add(event);
    }

    public List<Object> getEvents() {
        return List.copyOf(events);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> filter(Class<T> type) {
        return events.stream()
            .filter(type::isInstance)
            .map(e -> (T) e)
            .toList();
    }

    public int count() {
        return events.size();
    }

    public void reset() {
        events.clear();
    }
}
