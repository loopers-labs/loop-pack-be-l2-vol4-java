package com.loopers.benchmark;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 상품 재고를 동시에 차감할 때, 세 전략의 "특성 비용"을 분해해 비교하는 벤치마크.
 *
 * <ul>
 *   <li>A) 비관적 락: SELECT ... FOR UPDATE 로 행을 잠그고 읽은 값으로 계산해 덮어쓴다.
 *       <b>락 대기(+읽기) 누적 시간</b>을 따로 잰다 — FOR UPDATE 가 락을 얻을 때까지 블로킹되는 시간.</li>
 *   <li>B) 낙관적 락: version 을 읽고 {@code WHERE version = old} 로 compare-and-set, 충돌 시 재시도.
 *       <b>version 충돌 재시도 횟수와 재시도에 버린 누적 시간</b>을 잰다 — "재시도까지 해서 성공한" 비용.
 *       재고 소진(현재 &lt; 1)은 재시도가 아니라 정상 품절로 처리한다.</li>
 *   <li>C) 조건부 원자 UPDATE: UPDATE quantity = quantity - 1 WHERE quantity &gt;= 1. 단일 statement.</li>
 * </ul>
 *
 * <p>{@code INITIAL < TOTAL_OPS} 로 두어 막판 0 근처 경합과 품절(0행/소진) 경로까지 측정한다.
 * 세 전략 모두 정확성(성공 = 재고만큼, 최종 = 0, 음수 미발생)을 단언하고, 시간은 출력만 한다.
 * 수동 실행: {@code ./gradlew :apps:commerce-api:test --tests "*StockDecrementStrategyBenchmarkTest"}
 */
@SpringBootTest
class StockDecrementStrategyBenchmarkTest {

    private static final long PRODUCT_ID = 1L;
    private static final int THREADS = 8;
    private static final int OPS_PER_THREAD = 1_000;
    private static final int TOTAL_OPS = THREADS * OPS_PER_THREAD;
    /** 수요(TOTAL_OPS)보다 적게 잡아 막판 0 경합·품절 경로를 측정한다. */
    private static final long INITIAL = TOTAL_OPS * 3L / 4;          // 6,000 (품절 2,000건 발생)
    private static final long EXPECTED_SUCCESS = Math.min(INITIAL, TOTAL_OPS);
    private static final long EXPECTED_REMAIN = Math.max(0, INITIAL - TOTAL_OPS);

    @Autowired
    private DataSource dataSource;

    // 측정마다 resetStock 에서 0 으로 초기화되는 누적 카운터.
    private final AtomicLong success = new AtomicLong();             // 차감 성공 건수
    private final AtomicLong stockout = new AtomicLong();            // 재고 소진으로 차감 못 한 건수
    private final AtomicLong busyNanos = new AtomicLong();           // op 1건 처리 시간(품절 포함) 누적
    private final AtomicLong pessimisticLockWaitNanos = new AtomicLong(); // A) FOR UPDATE 락 대기+읽기 누적
    private final AtomicLong optimisticRetries = new AtomicLong();   // B) version 충돌 재시도 횟수
    private final AtomicLong optimisticRetryWasteNanos = new AtomicLong(); // B) 실패한 재시도에 버린 시간 누적

    @Test
    void compareDecrementStrategies() throws Exception {
        Result pessimistic = measure(this::pessimisticDecrement);
        Result optimistic = measure(this::optimisticDecrement);
        Result atomic = measure(this::atomicDecrement);

        long pessimisticTps = EXPECTED_SUCCESS * 1000L / Math.max(pessimistic.elapsedMs, 1);
        long optimisticTps = EXPECTED_SUCCESS * 1000L / Math.max(optimistic.elapsedMs, 1);
        long atomicTps = EXPECTED_SUCCESS * 1000L / Math.max(atomic.elapsedMs, 1);

        System.out.println("\n========= STOCK DECREMENT STRATEGY (threads=" + THREADS
                + ", ops=" + TOTAL_OPS + ", initial=" + INITIAL + " → 품절 " + (TOTAL_OPS - EXPECTED_SUCCESS) + "건) =========");
        System.out.printf("A) 비관적 락   : 전체 %6d ms | %6d ops/s | op평균 %6.3f ms | 락대기 누적 %6d ms (op평균 %.3f ms)%n",
                pessimistic.elapsedMs, pessimisticTps, avgOpMs(pessimistic.busyNanos),
                pessimistic.lockWaitNanos / 1_000_000, avgMs(pessimistic.lockWaitNanos, TOTAL_OPS));
        System.out.printf("B) 낙관적 락   : 전체 %6d ms | %6d ops/s | op평균 %6.3f ms | 재시도 %d회(성공당 %.2f회) 재시도낭비 누적 %d ms%n",
                optimistic.elapsedMs, optimisticTps, avgOpMs(optimistic.busyNanos),
                optimistic.retries, (double) optimistic.retries / Math.max(EXPECTED_SUCCESS, 1),
                optimistic.retryWasteNanos / 1_000_000);
        System.out.printf("C) 조건부 원자 : 전체 %6d ms | %6d ops/s | op평균 %6.3f ms | 단일 UPDATE%n",
                atomic.elapsedMs, atomicTps, avgOpMs(atomic.busyNanos));
        printRanking(pessimisticTps, optimisticTps, atomicTps);
        System.out.println("====================================================================\n");

        // 정확성: 세 전략 모두 재고만큼만 성공하고, 최종 재고는 0(음수 미발생), 성공+품절 = 전체 시도.
        assertResult(pessimistic);
        assertResult(optimistic);
        assertResult(atomic);
    }

    private void assertResult(Result r) {
        assertThat(r.success).isEqualTo(EXPECTED_SUCCESS);
        assertThat(r.stockout).isEqualTo(TOTAL_OPS - EXPECTED_SUCCESS);
        assertThat(r.remain).isEqualTo(EXPECTED_REMAIN);
    }

    /** 세 전략을 실제 처리량 내림차순으로 정렬해 출력한다(하드코딩 순서 금지). 배수는 최저 처리량 기준. */
    private void printRanking(long pessimisticTps, long optimisticTps, long atomicTps) {
        record Strategy(String name, long tps) {
        }
        List<Strategy> ranked = new ArrayList<>(List.of(
                new Strategy("A 비관락", pessimisticTps),
                new Strategy("B 낙관락", optimisticTps),
                new Strategy("C 원자", atomicTps)));
        ranked.sort(Comparator.comparingLong(Strategy::tps).reversed());

        long base = ranked.get(ranked.size() - 1).tps();
        StringBuilder sb = new StringBuilder("=> 처리량 ");
        for (int i = 0; i < ranked.size(); i++) {
            Strategy s = ranked.get(i);
            sb.append(String.format("%s(%.2f배)", s.name(), (double) s.tps() / Math.max(base, 1)));
            if (i < ranked.size() - 1) {
                sb.append(" > ");
            }
        }
        sb.append("  [최저 처리량 기준]");
        System.out.println(sb);
    }

    private double avgOpMs(long totalNanos) {
        return avgMs(totalNanos, TOTAL_OPS);
    }

    private double avgMs(long totalNanos, long count) {
        return totalNanos / 1_000_000.0 / Math.max(count, 1);
    }

    private interface Decrement {
        void apply(Connection conn) throws Exception;
    }

    /** A) 비관적 락: FOR UPDATE 로 잠그고 읽은 값으로 계산해 덮어쓴다. 락 대기+읽기 시간을 따로 잰다. */
    private void pessimisticDecrement(Connection conn) throws Exception {
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            long lockStart = System.nanoTime();
            long current;
            try (ResultSet rs = st.executeQuery(
                    "SELECT quantity FROM product_stocks WHERE product_id = " + PRODUCT_ID + " FOR UPDATE")) {
                rs.next();
                current = rs.getLong(1);
            }
            pessimisticLockWaitNanos.addAndGet(System.nanoTime() - lockStart);
            if (current >= 1) {
                st.executeUpdate("UPDATE product_stocks SET quantity = " + (current - 1)
                        + " WHERE product_id = " + PRODUCT_ID);
                success.incrementAndGet();
            } else {
                stockout.incrementAndGet();
            }
        }
        conn.commit();
    }

    /**
     * B) 낙관적 락: version 을 읽고 {@code WHERE version = old} 로 CAS. 0 행이면 그 사이 다른 스레드가
     * version 을 올린 것이므로 다시 읽어 재시도한다(재시도 횟수·낭비 시간 계측). 재고 소진은 재시도가 아니다.
     */
    private void optimisticDecrement(Connection conn) throws Exception {
        conn.setAutoCommit(true);
        while (true) {
            long iterStart = System.nanoTime();
            long current;
            long version;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT quantity, version FROM product_stocks WHERE product_id = " + PRODUCT_ID)) {
                rs.next();
                current = rs.getLong(1);
                version = rs.getLong(2);
            }
            if (current < 1) {
                stockout.incrementAndGet();
                return;
            }
            int updated;
            try (Statement st = conn.createStatement()) {
                updated = st.executeUpdate(
                        "UPDATE product_stocks SET quantity = " + (current - 1) + ", version = " + (version + 1)
                                + " WHERE product_id = " + PRODUCT_ID + " AND version = " + version);
            }
            if (updated == 1) {
                success.incrementAndGet();
                return;
            }
            optimisticRetries.incrementAndGet();
            optimisticRetryWasteNanos.addAndGet(System.nanoTime() - iterStart);
        }
    }

    /** C) 조건부 원자 UPDATE: 단일 statement 로 조건부 차감. 0 행이면 재고 소진. */
    private void atomicDecrement(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE product_stocks SET quantity = quantity - 1 WHERE product_id = ? AND quantity >= 1")) {
            ps.setLong(1, PRODUCT_ID);
            if (ps.executeUpdate() == 1) {
                success.incrementAndGet();
            } else {
                stockout.incrementAndGet();
            }
        }
    }

    /** THREADS 개 스레드가 OPS_PER_THREAD 회씩 동시에 차감하는 데 걸린 시간과 카운터를 잰다. */
    private Result measure(Decrement op) throws Exception {
        resetStock();
        success.set(0);
        stockout.set(0);
        busyNanos.set(0);
        pessimisticLockWaitNanos.set(0);
        optimisticRetries.set(0);
        optimisticRetryWasteNanos.set(0);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                ready.countDown();
                try (Connection conn = dataSource.getConnection()) {
                    start.await();
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        long opStart = System.nanoTime();
                        op.apply(conn);
                        busyNanos.addAndGet(System.nanoTime() - opStart);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        long startNanos = System.nanoTime();
        start.countDown();
        done.await();
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        pool.shutdownNow();

        return new Result(elapsedMs, success.get(), stockout.get(), busyNanos.get(),
                pessimisticLockWaitNanos.get(), optimisticRetries.get(), optimisticRetryWasteNanos.get(),
                currentQuantity());
    }

    private record Result(long elapsedMs, long success, long stockout, long busyNanos,
                          long lockWaitNanos, long retries, long retryWasteNanos, long remain) {
    }

    private void resetStock() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            // 낙관적 락 전략용 version 컬럼(없으면 추가). A/C 는 무시한다.
            try {
                exec(conn, "ALTER TABLE product_stocks ADD COLUMN version BIGINT NOT NULL DEFAULT 0");
            } catch (Exception alreadyExists) {
                // 컬럼이 이미 있으면 무시
            }
            exec(conn, "TRUNCATE TABLE product_stocks");
            exec(conn, "INSERT INTO product_stocks (product_id, quantity, version, created_at, updated_at) "
                    + "VALUES (" + PRODUCT_ID + ", " + INITIAL + ", 0, NOW(), NOW())");
        }
    }

    private long currentQuantity() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT quantity FROM product_stocks WHERE product_id = " + PRODUCT_ID)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void exec(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }
}
