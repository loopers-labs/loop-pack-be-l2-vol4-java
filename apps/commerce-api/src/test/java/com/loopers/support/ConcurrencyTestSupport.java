package com.loopers.support;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** 동시성 테스트용 헬퍼. 모든 스레드를 동시에 출발시켜 경합을 유도하고 성공/실패 건수를 집계한다. */
public final class ConcurrencyTestSupport {

    private ConcurrencyTestSupport() {}

    public record Result(int success, int failure) {}

    public static Result runConcurrently(int threads, IntConsumer task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, 32));
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int index = i;
            pool.submit(
                () -> {
                    ready.countDown();
                    try {
                        start.await();
                        task.accept(index);
                        success.incrementAndGet();
                    } catch (Exception e) {
                        failure.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
        }

        ready.await();
        start.countDown(); // 모든 스레드 동시 출발
        done.await();
        pool.shutdown();

        return new Result(success.get(), failure.get());
    }
}
