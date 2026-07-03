package com.loopers.domain.eventpublish;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakeOutboxRepository implements OutboxRepository {

    private final List<OutboxMessage> store = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public OutboxMessage save(OutboxMessage message) {
        if (message.getId() == null || message.getId() == 0L) {
            assignId(message, sequence.incrementAndGet());
            store.add(message);
        }
        return message;
    }

    @Override
    public Optional<OutboxMessage> findById(Long id) {
        return store.stream().filter(m -> m.getId().equals(id)).findFirst();
    }

    @Override
    public List<OutboxMessage> findPendingBatch(int limit) {
        return store.stream()
            .filter(m -> m.getStatus() == OutboxStatus.PENDING)
            .sorted(Comparator.comparing(OutboxMessage::getId))
            .limit(limit)
            .toList();
    }

    @Override
    public Optional<OutboxMessage> findByEventId(String eventId) {
        return store.stream()
            .filter(m -> m.getEventId().equals(eventId))
            .findFirst();
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
