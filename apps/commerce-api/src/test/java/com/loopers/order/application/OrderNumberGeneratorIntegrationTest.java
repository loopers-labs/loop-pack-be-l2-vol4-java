package com.loopers.order.application;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class OrderNumberGeneratorIntegrationTest {

    private final OrderNumberGenerator orderNumberGenerator;
    private final DatabaseCleanUp databaseCleanUp;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    public OrderNumberGeneratorIntegrationTest(
            OrderNumberGenerator orderNumberGenerator,
            DatabaseCleanUp databaseCleanUp,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate
    ) {
        this.orderNumberGenerator = orderNumberGenerator;
        this.databaseCleanUp = databaseCleanUp;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    @Test
    @DisplayName("연속 채번하면 같은 날짜 안에서 순번이 1, 2, 3 으로 증가한다")
    void whenGenerateRepeatedly_thenSequenceIncrementsWithinSameDate() {
        String first = orderNumberGenerator.generate();
        String second = orderNumberGenerator.generate();
        String third = orderNumberGenerator.generate();

        assertThat(first).isEqualTo(today() + "-000001");
        assertThat(second).isEqualTo(today() + "-000002");
        assertThat(third).isEqualTo(today() + "-000003");
    }

    @Test
    @DisplayName("채번은 중복 없이 고유한 주문번호를 발급한다")
    void whenGenerateManyTimes_thenAllNumbersAreUnique() {
        var numbers = new java.util.HashSet<String>();
        for (int i = 0; i < 20; i++) {
            numbers.add(orderNumberGenerator.generate());
        }

        assertThat(numbers).hasSize(20);
    }

    @Test
    @DisplayName("동시에 채번해도 중복 없이 고유한 주문번호를 발급한다")
    void whenGenerateConcurrently_thenAllNumbersAreUnique() throws InterruptedException {
        int threads = 100;
        Set<String> numbers = ConcurrentHashMap.newKeySet();
        AtomicInteger errors = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    numbers.add(orderNumberGenerator.generate());
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        assertAll(
                () -> assertThat(errors.get()).isZero(),
                () -> assertThat(numbers).hasSize(threads)   // 중복 채번이면 size < threads
        );
    }

    @Test
    @DisplayName("채번은 주문번호 접두사와 같은 날짜의 시퀀스 행을 증가시킨다 (TZ 시프트 회귀)")
    void whenGenerate_thenIncrementsRowOfSameDateAsPrefix() {
        // JVM(Asia/Seoul)과 DB(UTC)의 시간대가 다를 때, LocalDate 바인딩이 하루 밀려
        // 접두사(20260708)와 다른 날짜(2026-07-07) 행을 증가시키는 버그의 회귀 가드.
        String orderNumber = orderNumberGenerator.generate();
        String prefixDate = orderNumber.substring(0, 8);                      // yyyyMMdd
        String isoDate = LocalDate.parse(prefixDate, DateTimeFormatter.BASIC_ISO_DATE).toString();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_number_sequences WHERE order_date = ?", Integer.class, isoDate);

        assertThat(count).isEqualTo(1);
    }
}
