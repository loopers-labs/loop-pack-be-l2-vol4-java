package com.loopers.repository;

import com.loopers.model.UserHistory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Repository
public class InMemoryUserHistoryRepository {

    private final List<UserHistory> histories = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);

    public UserHistory record(int userId, String changedField, String oldValue, String newValue) {
        UserHistory h = new UserHistory();
        h.setId(idCounter.getAndIncrement());
        h.setUserId(userId);
        h.setChangedField(changedField);
        h.setOldValue(oldValue);
        h.setNewValue(newValue);
        h.setChangedAt(Instant.now().toString());
        histories.add(h);
        return h;
    }

    public List<UserHistory> findByUserId(int userId) {
        List<UserHistory> result = histories.stream().filter(h -> h.getUserId() == userId).collect(Collectors.toList());
        Collections.reverse(result);
        return result;
    }

    public void clear() {
        histories.clear();
        idCounter.set(1);
    }
}
