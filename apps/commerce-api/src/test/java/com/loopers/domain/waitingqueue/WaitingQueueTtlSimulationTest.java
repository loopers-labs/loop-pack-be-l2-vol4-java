package com.loopers.domain.waitingqueue;

import com.loopers.config.waitingqueue.WaitingQueueProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토큰 TTL 스윕 시뮬레이션(부하테스트 대체, docs/week8 §D1 튜닝 근거).
 *
 * <p>방류형 정책(매 tick마다 대기열 앞에서 N명 방류)을 {@link ThroughputPolicy}로 그대로 돌리되,
 * "발급된 토큰이 안 쓰이는" 상황(이탈·느린 주문)을 모델링해 TTL이 배수시간/UX에 미치는 영향을 본다.
 * 방류형에선 방류 레이트(N/M)가 입장 속도를 고정하고, TTL은 느린 정상 유저를 바운스시키는 마감으로 작동한다.
 *
 * <p>공정 비교를 위해 유저 특성(이탈 여부·주문 지연)을 고정 시드로 한 번만 뽑고 모든 TTL 런에서 재사용한다.
 * 결과는 System.out(JUnit system-out)으로 표를 출력한다. 결정적(시드 고정)이라 재현 가능.
 */
class WaitingQueueTtlSimulationTest {

    // ---- 시나리오 파라미터 ----
    private static final int N = 5_000;              // 플래시 크라우드 인원(t=0 동시 진입)
    private static final int RELEASE_N = 30;         // = ThroughputPolicy.releaseSize() (매 tick 방류 인원)
    private static final long TICK_MS = 2_000;       // 방류 주기 M
    private static final long DT_MS = 250;           // 시뮬레이션 시간 스텝
    private static final double P_ABANDON = 0.15;    // "탭 닫는" 이탈자 비율
    private static final double LATENCY_MEAN_MS = 6_000; // 정상 유저 주문 지연 평균(지수분포)
    private static final int MAX_RETRIES = 5;        // 바운스 후 재진입 상한(초과 시 실패)
    private static final long SAFETY_LIMIT_MS = 6L * 3600 * 1000;
    private static final int[] TTL_SWEEP_SEC = {8, 15, 30, 60, 120};
    private static final long SEED = 42L;

    private static final class User {
        final boolean abandon;
        final long latencyMs;   // 이탈자면 무의미
        long enterMs;           // 최초 진입 시각(대기 측정 기준, 재진입해도 유지)
        int retries;
        User(boolean abandon, long latencyMs) {
            this.abandon = abandon;
            this.latencyMs = latencyMs;
        }
    }

    private static final class Token {
        final int userIdx;
        final long expireMs;
        final long orderMs; // 정상 유저의 주문 시도 시각(=admit+latency), 이탈자는 Long.MAX
        Token(int userIdx, long expireMs, long orderMs) {
            this.userIdx = userIdx;
            this.expireMs = expireMs;
            this.orderMs = orderMs;
        }
    }

    @Test
    @DisplayName("TTL 스윕: 처리량/이탈낭비/바운스 트레이드오프 표 출력")
    void sweepTokenTtl() {
        // releaseSize가 실제 정책값과 일치하는지 확인(문서 D2). 안전캡(hardMax)은 이 sim에서 미모델(0).
        ThroughputPolicy policy = new ThroughputPolicy(
            new WaitingQueueProperties(true, 30, 2, 30, 2, 0));
        assertThat(policy.releaseSize()).isEqualTo(RELEASE_N);

        // 유저 특성 1회 생성 → 모든 TTL 런에서 재사용(공정 비교)
        boolean[] abandon = new boolean[N];
        long[] latency = new long[N];
        Random gen = new Random(SEED);
        for (int i = 0; i < N; i++) {
            abandon[i] = gen.nextDouble() < P_ABANDON;
            latency[i] = expSample(gen, LATENCY_MEAN_MS);
        }

        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT,
            "%nTTL 스윕 시뮬레이션 (N=%d, releaseN=%d, tick=%.0fs, 이탈=%.0f%%, 지연평균=%.0fs, seed=%d)%n",
            N, RELEASE_N, TICK_MS / 1000.0, P_ABANDON * 100, LATENCY_MEAN_MS / 1000, SEED));
        out.append("─".repeat(104)).append('\n');
        out.append(String.format(Locale.ROOT,
            "%5s │ %9s │ %9s │ %8s │ %10s │ %10s │ %9s │ %8s │ %8s%n",
            "TTL", "완료", "이탈", "실패", "발급수", "처리량/s", "배수시간", "활성피크", "활성말기"));
        out.append(String.format(Locale.ROOT,
            "%5s │ %9s │ %9s │ %8s │ %10s │ %10s │ %9s │ %8s │ %8s%n",
            "(s)", "(주문)", "(탭닫음)", "(TTL초과)", "(토큰)", "(orders)", "(min)", "(동시)", "(정상상태)"));
        out.append("─".repeat(104)).append('\n');

        Result minFail = null;
        for (int ttlSec : TTL_SWEEP_SEC) {
            Result r = run(ttlSec, abandon, latency);
            out.append(String.format(Locale.ROOT,
                "%5d │ %9d │ %9d │ %8d │ %10d │ %10.2f │ %9.1f │ %8d │ %8d%n",
                ttlSec, r.completed, r.abandoned, r.failed, r.admissions,
                r.throughputPerSec, r.drainSec / 60.0, r.peakActive, r.steadyActive));

            // 보존 법칙: 모든 유저는 완료/이탈/실패 중 하나로 귀결
            assertThat(r.completed + r.abandoned + r.failed).isEqualTo(N);
            if (minFail == null || r.failed < minFail.failed) {
                minFail = r;
            }
        }
        out.append("─".repeat(104)).append('\n');
        out.append("[방류형 재해석] 방류 레이트 N/M가 입장 속도를 고정한다(활성 상한 gate 없음). 그래서:\n");
        out.append(" · TTL↑ → 느린 정상 유저 바운스↓ → 실패↓·재발급 낭비↓ (정원제와 달리 처리량을 깎지 않는다).\n");
        out.append(" · 대신 TTL↑ → 동시 활성(토큰 보유자) 피크↑ ≈ (방류레이트 N/M) × TTL. 이게 방류형에서 TTL의 유일한 비용.\n");
        out.append(" · 단 활성 피크는 '토큰 보유자 수'이지 DB 동시부하가 아니다. DB 동시성 ≈ 주문레이트 × 처리시간 ≈ (N/M)×avgProcess.\n");
        out.append(String.format(Locale.ROOT,
            " → 실패 최소는 TTL=%ds(실패 %d명)이나, 방류형에선 실패가 TTL에 단조 감소하므로 '스위트스팟'이 아니라 "
                + "'느린유저 꼬리를 덮는 최소 TTL'을 고르고, N/M×TTL이 메모리/Redis 예산 안에 들게 잡는다.\n",
            minFail.ttlSec, minFail.failed));

        System.out.println(out);
    }

    private Result run(int ttlSec, boolean[] abandon, long[] latency) {
        long ttlMs = ttlSec * 1000L;
        User[] users = new User[N];
        ArrayDeque<Integer> queue = new ArrayDeque<>(N);
        for (int i = 0; i < N; i++) {
            users[i] = new User(abandon[i], latency[i]);
            users[i].enterMs = 0;
            queue.addLast(i);
        }
        List<Token> active = new ArrayList<>(RELEASE_N + 4);
        List<Long> waits = new ArrayList<>(N);

        int completed = 0, abandoned = 0, failed = 0;
        long admissions = 0;
        long now = 0;
        int peakActive = 0;      // 동시 활성(토큰 보유자) 최대치
        long steadySum = 0;      // 백로그(지속 방류) 구간 활성 누적 → 평균이 정상상태 근사
        int steadyTicks = 0;

        while (now <= SAFETY_LIMIT_MS) {
            // 1) 활성 토큰 처리(주문 성공 / 만료)
            for (int t = active.size() - 1; t >= 0; t--) {
                Token tok = active.get(t);
                User u = users[tok.userIdx];
                if (!u.abandon && tok.orderMs <= now && tok.orderMs <= tok.expireMs) {
                    // 주문 성공 → 슬롯 회수
                    completed++;
                    waits.add(now - u.enterMs);
                    active.remove(t);
                } else if (now >= tok.expireMs) {
                    // 만료 → 슬롯 회수
                    active.remove(t);
                    if (u.abandon) {
                        abandoned++; // 이탈자는 떠남
                    } else {
                        // 느린 정상 유저 바운스 → 재진입(공정성) 또는 재시도 소진 시 실패
                        u.retries++;
                        if (u.retries > MAX_RETRIES) {
                            failed++;
                        } else {
                            queue.addLast(tok.userIdx);
                        }
                    }
                }
            }
            // 2) 방류 틱: 활성 점유량과 무관하게 대기열 앞에서 N명 방류(rate-based)
            if (now % TICK_MS == 0) {
                int batch = RELEASE_N;
                for (int j = 0; j < batch && !queue.isEmpty(); j++) {
                    int idx = queue.pollFirst();
                    User u = users[idx];
                    long expireMs = now + ttlMs;
                    long orderMs = u.abandon ? Long.MAX_VALUE : now + u.latencyMs;
                    active.add(new Token(idx, expireMs, orderMs));
                    admissions++;
                }
            }
            peakActive = Math.max(peakActive, active.size());
            if (!queue.isEmpty()) {           // 지속 방류(백로그) 구간 동안 활성 표본 → 평균이 정상상태 근사
                steadySum += active.size();
                steadyTicks++;
            }
            if (queue.isEmpty() && active.isEmpty()) {
                break;
            }
            now += DT_MS;
        }

        Result r = new Result();
        r.ttlSec = ttlSec;
        r.completed = completed;
        r.abandoned = abandoned;
        r.failed = failed;
        r.admissions = admissions;
        r.drainSec = now / 1000.0;
        r.throughputPerSec = completed / Math.max(1.0, r.drainSec);
        r.p95WaitSec = percentile(waits, 95) / 1000.0;
        r.peakActive = peakActive;
        r.steadyActive = (int) (steadySum / Math.max(1, steadyTicks));
        return r;
    }

    private static long expSample(Random gen, double meanMs) {
        double u = Math.max(1e-9, gen.nextDouble());
        return (long) (-meanMs * Math.log(u));
    }

    private static long percentile(List<Long> values, int p) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, idx)));
    }

    private static final class Result {
        int ttlSec;
        int completed;
        int abandoned;
        int failed;
        long admissions;
        double drainSec;
        double throughputPerSec;
        double p95WaitSec;
        int peakActive;
        int steadyActive;
    }
}
