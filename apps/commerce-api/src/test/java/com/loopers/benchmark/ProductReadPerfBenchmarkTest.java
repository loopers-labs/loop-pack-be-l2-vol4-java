package com.loopers.benchmark;

import com.loopers.domain.productrank.ProductRankRepository;
import com.loopers.support.seed.ProductSeeder;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 읽기 성능 AS-IS 근거. c(현재 cross-table 조인) 구조의 likes_desc 정렬은
 * 정렬 키(like_count)가 다른 테이블에 있어 어떤 인덱스로도 정렬을 덮을 수 없고,
 * EXPLAIN 에 Using filesort 가 남는다. (10만건 시간 측정은 별도 수동 벤치/분석에서 수행)
 */
@SpringBootTest
class ProductReadPerfBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(ProductReadPerfBenchmarkTest.class);

    @Autowired ProductSeeder seeder;
    @Autowired ProductRankRepository productRankRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("c(cross-table 조인) likes_desc 는 인덱스로 정렬을 못 덮어 filesort 가 발생한다 (AS-IS)")
    void c_baseline_likes_desc_uses_filesort() {
        seeder.seed(2_000, 100);

        String sql = """
            SELECT p.* FROM product p
            LEFT JOIN product_like_count plc ON plc.product_id = p.id
            WHERE p.brand_id = 42 AND p.deleted_at IS NULL
            ORDER BY COALESCE(plc.like_count, 0) DESC, p.id DESC
            LIMIT 20
            """;

        List<Map<String, Object>> plan = jdbc.queryForList("EXPLAIN " + sql);
        plan.forEach(row -> log.info("[c-EXPLAIN] {}", row));

        boolean hasFilesort = plan.stream()
            .map(r -> String.valueOf(r.get("Extra")))
            .anyMatch(e -> e != null && e.toLowerCase().contains("filesort"));

        assertThat(hasFilesort)
            .as("cross-table 정렬이라 filesort 가 떠야 한다 (AS-IS 근거)")
            .isTrue();
    }

    @Disabled("수동 벤치마크. 측정 수치는 Benchmark Report 참고")
    @DisplayName("읽기 경로 벤치: c vs b EXPLAIN·시간 + OFFSET vs 키셋 딥페이지")
    @Test
    void benchmark_read_paths() {
        int n = 30_000;
        seeder.seed(n, 200);
        productRankRepository.rebuildFromSource();
        jdbc.execute("ANALYZE TABLE product");
        jdbc.execute("ANALYZE TABLE product_like_count");
        jdbc.execute("ANALYZE TABLE product_rank");

        // (1) brand 필터 likes_desc: c(cross-table 조인) vs b(읽기모델)
        long brandId = 42L;
        String cSql = "SELECT p.* FROM product p LEFT JOIN product_like_count plc ON plc.product_id = p.id "
            + "WHERE p.brand_id = " + brandId + " AND p.deleted_at IS NULL "
            + "ORDER BY COALESCE(plc.like_count, 0) DESC, p.id DESC LIMIT 20";
        String bSql = "SELECT product_id FROM product_rank WHERE brand_id = " + brandId + " "
            + "ORDER BY like_count DESC, product_id DESC LIMIT 20";
        explain("c", cSql);
        explain("b", bSql);
        log.info("[BENCH n={} brand] c={}us  b={}us", n, avgMicros(cSql), avgMicros(bSql));

        // (2) 무필터 likes_desc 딥페이지: OFFSET vs 키셋 (같은 위치=10000번째 다음 20개)
        int deep = 10_000;
        String offsetSql = "SELECT product_id FROM product_rank "
            + "ORDER BY like_count DESC, product_id DESC LIMIT 20 OFFSET " + deep;
        Map<String, Object> at = jdbc.queryForMap(
            "SELECT like_count, product_id FROM product_rank "
            + "ORDER BY like_count DESC, product_id DESC LIMIT 1 OFFSET " + (deep - 1));
        long lc = ((Number) at.get("like_count")).longValue();
        long pid = ((Number) at.get("product_id")).longValue();
        String keysetSql = "SELECT product_id FROM product_rank "
            + "WHERE (like_count < " + lc + " OR (like_count = " + lc + " AND product_id < " + pid + ")) "
            + "ORDER BY like_count DESC, product_id DESC LIMIT 20";
        explain("offset", offsetSql);
        explain("keyset", keysetSql);
        log.info("[BENCH deep={}] OFFSET={}us  keyset={}us", deep, avgMicros(offsetSql), avgMicros(keysetSql));
    }

    private void explain(String tag, String sql) {
        for (Map<String, Object> row : jdbc.queryForList("EXPLAIN " + sql)) {
            log.info("[BENCH-EXPLAIN {}] table={} type={} key={} rows={} Extra={}",
                tag, row.get("table"), row.get("type"), row.get("key"), row.get("rows"), row.get("Extra"));
        }
    }

    /** 워밍업 후 30회 평균(마이크로초). ms 단위는 너무 거칠어 us 로 본다. */
    private long avgMicros(String sql) {
        for (int i = 0; i < 5; i++) {
            jdbc.queryForList(sql);
        }
        int runs = 30;
        long t0 = System.nanoTime();
        for (int i = 0; i < runs; i++) {
            jdbc.queryForList(sql);
        }
        return (System.nanoTime() - t0) / runs / 1_000;
    }
}
