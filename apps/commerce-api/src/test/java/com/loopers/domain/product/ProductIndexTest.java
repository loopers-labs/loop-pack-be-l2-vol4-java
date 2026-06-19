package com.loopers.domain.product;

import com.loopers.support.seed.ProductSeeder;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductIndexTest {

    @Autowired ProductSeeder seeder;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        seeder.seed(5_000, 100);
        jdbc.execute("ANALYZE TABLE product");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("brand 필터 + price 정렬은 (brand_id, price) 복합 인덱스를 타고 filesort 가 없다")
    @Test
    void brand_price_uses_composite_index_without_filesort() {
        List<Map<String, Object>> plan = jdbc.queryForList(
            "EXPLAIN SELECT * FROM product WHERE brand_id = 7 AND deleted_at IS NULL ORDER BY price ASC LIMIT 20");

        Map<String, Object> row = plan.get(0);
        String key = String.valueOf(row.get("key"));
        String extra = String.valueOf(row.get("Extra"));

        assertThat(key).contains("brand_price");
        assertThat(extra == null ? "" : extra.toLowerCase()).doesNotContain("filesort");
    }
}
