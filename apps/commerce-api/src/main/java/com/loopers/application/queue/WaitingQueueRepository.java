package com.loopers.application.queue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface WaitingQueueRepository {
    boolean enqueueIfAbsent(String userLoginId);
    Optional<Long> findRank(String userLoginId);
    long countWaitingUsers();
    List<String> popWaitingUsers(int count);
    void issueEntryToken(String userLoginId, String token, Duration ttl);
    Optional<String> findEntryToken(String userLoginId);
    boolean claimEntryToken(String userLoginId, String token);
    boolean completeEntryToken(String userLoginId, String token);
    boolean releaseEntryTokenClaim(String userLoginId, String token);
}
