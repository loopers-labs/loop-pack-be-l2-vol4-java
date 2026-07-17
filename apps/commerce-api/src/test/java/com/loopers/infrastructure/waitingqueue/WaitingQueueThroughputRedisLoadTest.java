package com.loopers.infrastructure.waitingqueue;

import com.loopers.domain.waitingqueue.EntryTokenRepository;
import com.loopers.domain.waitingqueue.WaitingQueueService;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Locale;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 방류형 처리량 부하테스트(인프로세스, 실제 Redis/Testcontainers).
 *
 * <p>플래시 크라우드가 한꺼번에 몰린 상황에서, 스케줄러를 빠르게 폴링하며 <b>실제 입장(방류) 레이트가
 * N/M로 수렴</b>하는지 측정한다. Lua 고정 윈도우 레이트리밋이 실제 벽시계 기준으로 동작하므로,
 * 폴링을 아무리 자주 해도 M초 윈도우당 방류는 N을 넘지 못한다. 즉 "대기열에 5000명이 쌓여 있어도
 * 초당 N/M명씩만 입장한다"는 admission control을 정량 검증한다.
 *
 * <p>설정: N=50, M=1s → 목표 50/s. 토큰 TTL을 크게(300s) 두어 측정 중 만료로 활성이 줄지 않게 하고,
 * 안전캡은 0(비활성)으로 순수 방류 레이트만 본다.
 */
@SpringBootTest(properties = {
    "waiting-queue.enabled=true",
    "waiting-queue.release-size=50",
    "waiting-queue.scheduler-interval-seconds=1",
    "waiting-queue.token-ttl-seconds=300",
    "waiting-queue.hard-max-active=0"
})
class WaitingQueueThroughputRedisLoadTest {

    private static final int CROWD = 5_000;
    private static final int RELEASE_N = 50;      // = release-size
    private static final int INTERVAL_M = 1;      // = scheduler-interval-seconds(s)
    private static final long RUN_MS = 6_000;     // 측정 시간(약 6개 윈도우)
    private static final long POLL_MS = 50;       // 스케줄러 폴링 주기(윈도우보다 촘촘히)

    @Autowired WaitingQueueService service;
    @Autowired EntryTokenRepository tokens;
    @Autowired RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("플래시 크라우드 5000명이 쌓여도 입장 레이트는 N/M(50/s)로 수렴한다")
    @Test
    void admissionRateConvergesToReleaseRate() throws InterruptedException {
        for (long u = 1; u <= CROWD; u++) {
            service.enter(u);
        }

        // 벽시계 초 버킷별 방류량(= Lua 윈도우와 정렬: floor(ms/1000))
        TreeMap<Long, Integer> perSecond = new TreeMap<>();
        long startMs = System.currentTimeMillis();
        long deadline = startMs + RUN_MS;
        int total = 0;
        while (System.currentTimeMillis() < deadline) {
            int n = service.issueBatch();       // 이번 윈도우 여유분만큼 방류(대부분 0, 새 윈도우 열릴 때만 N)
            if (n > 0) {
                total += n;
                perSecond.merge((System.currentTimeMillis() - startMs) / 1000, n, Integer::sum);
            }
            Thread.sleep(POLL_MS);
        }
        double elapsedSec = (System.currentTimeMillis() - startMs) / 1000.0;
        double rate = total / elapsedSec;

        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT,
            "%n[방류형 처리량 부하테스트] 크라우드=%d, N=%d, M=%ds → 목표 %d/s%n",
            CROWD, RELEASE_N, INTERVAL_M, RELEASE_N / INTERVAL_M));
        out.append("─".repeat(40)).append('\n');
        out.append(String.format(Locale.ROOT, "%6s │ %8s%n", "경과(s)", "방류(명)"));
        out.append("─".repeat(40)).append('\n');
        perSecond.forEach((sec, cnt) ->
            out.append(String.format(Locale.ROOT, "%6d │ %8d%n", sec, cnt)));
        out.append("─".repeat(40)).append('\n');
        out.append(String.format(Locale.ROOT,
            "총 방류=%d (%.1fs) → 실효 %.1f/s | 남은 대기=%d | 활성=%d%n",
            total, elapsedSec, rate, service.status().queueSize(), tokens.activeCountLive()));
        System.out.println(out);

        // ① 실효 입장 레이트가 목표 N/M 근방(폴링·윈도우 정렬 오차 감안 ±30%)
        assertThat(rate).isBetween(RELEASE_N * 0.7, RELEASE_N * 1.4);
        // ② 어떤 1초 버킷도 방류 상한(N=50)을 크게 넘지 않음 — 윈도우 경계 mis-binning 감안 슬랙 1개
        assertThat(perSecond.values()).allSatisfy(c ->
            assertThat(c).isLessThanOrEqualTo(RELEASE_N * 2));
        // ③ admission control 증거: 5000명이 쌓여 있어도 총 방류는 레이트에 묶여 큐가 안 빠짐
        assertThat(total).isLessThan(CROWD / 2);
        assertThat(service.status().queueSize()).isGreaterThan(CROWD - total - 1);
        // ④ 방류분 = 활성(측정 중 TTL 만료·소모 없음)
        assertThat(tokens.activeCountLive()).isEqualTo((long) total);
    }
}
