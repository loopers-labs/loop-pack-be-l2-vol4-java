package com.loopers.support.cache;

import java.time.Duration;
import java.util.function.Supplier;

public interface CacheStore {

    <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader);

    void evict(String key);
}
