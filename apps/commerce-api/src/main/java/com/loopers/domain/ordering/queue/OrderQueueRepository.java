package com.loopers.domain.ordering.queue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface OrderQueueRepository {

    Entry enter(String userId);

    Optional<Long> rank(String userId);

    long waitingCount();

    String issueToken(String userId, Duration ttl);

    Optional<String> findToken(String userId);

    boolean isValidToken(String userId, String token);

    void deleteToken(String userId);

    List<Admitted> admitNext(int count, Duration ttl);

    record Entry(String userId, long sequence, long rank) {
    }

    record Admitted(String userId, String token) {
    }
}
