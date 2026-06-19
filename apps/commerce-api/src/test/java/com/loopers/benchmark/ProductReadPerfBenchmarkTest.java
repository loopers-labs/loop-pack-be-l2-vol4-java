package com.loopers.benchmark;

import com.loopers.support.seed.ProductSeeder;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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
}
