package com.loopers.support.cache;

import java.time.Duration;
import java.util.Optional;

public interface CacheClient {

    <T> Optional<T> find(String key, Class<T> type);

    void save(String key, Object value, Duration ttl);

    void evict(String key);
}