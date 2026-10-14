package com.loopers.domain.eventhandled;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class FakeEventHandledRepository implements EventHandledRepository {

    private final List<EventHandledRecord> store = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup) {
        return store.stream().anyMatch(
            r -> r.getEventId().equals(eventId) && r.getConsumerGroup().equals(consumerGroup));
    }

    @Override
    public EventHandledRecord save(EventHandledRecord record) {
        if (existsByEventIdAndConsumerGroup(record.getEventId(), record.getConsumerGroup())) {
            throw new org.springframework.dao.DataIntegrityViolationException(
                "duplicate event_id+consumer_group");
        }
        if (record.getId() == null || record.getId() == 0L) {
            assignId(record, sequence.incrementAndGet());
            store.add(record);
        }
        return record;
    }

    public int size() {
        return store.size();
    }

    private static void assignId(Object entity, Long id) {
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null && !clazz.getSimpleName().equals("BaseEntity")) {
                clazz = clazz.getSuperclass();
            }
            Field idField = clazz.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Fake Repository: ID 주입 실패", e);
        }
    }
}
