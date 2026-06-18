package com.loopers.infrastructure.cache;

import com.loopers.support.cache.CacheStore;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisCacheStoreConcurrencyIntegrationTest {

    @Autowired
    private CacheStore cacheStore;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("같은 키에 동시 미스가 몰려도 loader는 한 번만 실행되고 모두 같은 값을 받는다")
    @Test
    void singleFlight_loaderRunsOnce_underConcurrentMiss() throws InterruptedException {
        // given
        int threadCount = 20;
        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        // when - 모든 스레드가 동시에 같은 미스 키를 조회
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    String value = cacheStore.getOrLoad("concurrency:key", String.class, Duration.ofMinutes(1), () -> {
                        loadCount.incrementAndGet();
                        sleepQuietly(200);
                        return "VALUE";
                    });
                    results.add(value);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        try {
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        // then
        assertThat(loadCount.get()).isEqualTo(1);
        assertThat(results).hasSize(threadCount).containsOnly("VALUE");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
