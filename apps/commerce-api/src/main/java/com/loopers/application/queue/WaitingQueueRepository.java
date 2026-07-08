package com.loopers.application.queue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface WaitingQueueRepository {
    boolean enqueueIfAbsent(String userLoginId, double score);
    Optional<Long> findRank(String userLoginId);
    long countWaitingUsers();
    List<String> popWaitingUsers(int count);
    void issueEntryToken(String userLoginId, String token, Duration ttl);
    Optional<String> findEntryToken(String userLoginId);
    void deleteEntryToken(String userLoginId);
}
