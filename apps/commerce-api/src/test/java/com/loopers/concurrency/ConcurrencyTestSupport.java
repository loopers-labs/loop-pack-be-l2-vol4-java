package com.loopers.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 동시성 테스트용 유틸 — N 스레드가 모두 출발 신호에 맞춰 race 를 시작하고, 결과를 집계한다.
 */
public final class ConcurrencyTestSupport {

    private ConcurrencyTestSupport() {}

    public record Result(int success, int failure, List<Throwable> errors) {}

    public static Result runRace(int threadCount, Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        List<Throwable> errors = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run();
                    success.incrementAndGet();
                } catch (Throwable e) {
                    failure.incrementAndGet();
                    synchronized (errors) {
                        errors.add(e);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown(); // 모두 준비 완료 — race 시작
        done.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        return new Result(success.get(), failure.get(), errors);
    }
}
