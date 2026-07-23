package com.loopers.tddstudy.support;

import com.loopers.tddstudy.domain.queue.EntryTokenRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FakeEntryTokenRepository implements EntryTokenRepository {

    private final Map<Long, String> store = new ConcurrentHashMap<>();  // TTL은 단위테스트에서 생략

    @Override public void issue(Long userId, String token, long ttlSeconds) { store.put(userId, token); }
    @Override public String find(Long userId) { return store.get(userId); }
    @Override public boolean consume(Long userId, String token) {
        return token != null && token.equals(store.get(userId)) && store.remove(userId) != null;
    }
}
