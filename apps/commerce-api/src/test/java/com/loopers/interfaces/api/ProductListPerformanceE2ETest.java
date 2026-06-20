package com.loopers.interfaces.api;

import com.loopers.support.dataloader.ProductDataLoader;
import com.loopers.utils.DatabaseCleanUp;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductListPerformanceE2ETest {

    // shallow pagination (1페이지)
    private static final String LATEST     = "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20";
    private static final String PRICE_ASC  = "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY price ASC LIMIT 20";
    private static final String LIKES_DESC = "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20";
    private static final String BRAND_LATEST     = "SELECT * FROM product WHERE brand_id = ? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20";
    private static final String BRAND_PRICE_ASC  = "SELECT * FROM product WHERE brand_id = ? AND deleted_at IS NULL ORDER BY price ASC LIMIT 20";
    private static final String BRAND_LIKES_DESC = "SELECT * FROM product WHERE brand_id = ? AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20";

    // deep pagination (OFFSET 40000 ≈ 전체 활성 데이터 약 57% 지점 / 브랜드 OFFSET 600 ≈ 브랜드당 활성 ~1400건의 43% 지점)
    private static final String DEEP_LATEST     = "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20 OFFSET 40000";
    private static final String DEEP_LIKES_DESC = "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 40000";
    private static final String DEEP_BRAND_LATEST     = "SELECT * FROM product WHERE brand_id = ? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20 OFFSET 600";
    private static final String DEEP_BRAND_LIKES_DESC = "SELECT * FROM product WHERE brand_id = ? AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 600";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final List<String> INDEX_NAMES = List.of(
        "idx_product_deleted_at_created_at",
        "idx_product_deleted_at_price",
        "idx_product_deleted_at_like_count",
        "idx_product_brand_id_deleted_at_created_at",
        "idx_product_brand_id_deleted_at_price",
        "idx_product_brand_id_deleted_at_like_count"
    );

    @BeforeAll
    void loadData() throws Exception {
        for (String name : INDEX_NAMES) {
            try { jdbcTemplate.execute("DROP INDEX " + name + " ON product"); } catch (Exception ignored) {}
        }
        new ProductDataLoader(jdbcTemplate).run(null);
    }

    @AfterAll
    void cleanUp() {
        databaseCleanUp.truncateAllTables();
    }

    // ========================
    // 인덱스 없이
    // ========================

    @Test
    @Order(1)
    @DisplayName("[인덱스 없이] latest 정렬")
    void withoutIndex_latest() {
        explain("인덱스 없이 | latest", LATEST);
    }

    @Test
    @Order(2)
    @DisplayName("[인덱스 없이] price_asc 정렬")
    void withoutIndex_priceAsc() {
        explain("인덱스 없이 | price_asc", PRICE_ASC);
    }

    @Test
    @Order(3)
    @DisplayName("[인덱스 없이] likes_desc 정렬")
    void withoutIndex_likesDesc() {
        explain("인덱스 없이 | likes_desc", LIKES_DESC);
    }

    @Test
    @Order(4)
    @DisplayName("[인덱스 없이] 브랜드 필터 + latest 정렬")
    void withoutIndex_brandFilter_latest() {
        explain("인덱스 없이 | 브랜드 + latest", BRAND_LATEST, getFirstBrandId());
    }

    @Test
    @Order(5)
    @DisplayName("[인덱스 없이] 브랜드 필터 + price_asc 정렬")
    void withoutIndex_brandFilter_priceAsc() {
        explain("인덱스 없이 | 브랜드 + price_asc", BRAND_PRICE_ASC, getFirstBrandId());
    }

    @Test
    @Order(6)
    @DisplayName("[인덱스 없이] 브랜드 필터 + likes_desc 정렬")
    void withoutIndex_brandFilter_likesDesc() {
        explain("인덱스 없이 | 브랜드 + likes_desc", BRAND_LIKES_DESC, getFirstBrandId());
    }

    // ========================
    // ASC 인덱스 추가
    // ========================

    @Test
    @Order(7)
    @DisplayName("인덱스 추가")
    void createIndexes() {
        jdbcTemplate.execute("CREATE INDEX idx_product_deleted_at_created_at ON product (deleted_at, created_at)");
        jdbcTemplate.execute("CREATE INDEX idx_product_deleted_at_price ON product (deleted_at, price)");
        jdbcTemplate.execute("CREATE INDEX idx_product_deleted_at_like_count ON product (deleted_at, like_count)");
        jdbcTemplate.execute("CREATE INDEX idx_product_brand_id_deleted_at_created_at ON product (brand_id, deleted_at, created_at)");
        jdbcTemplate.execute("CREATE INDEX idx_product_brand_id_deleted_at_price ON product (brand_id, deleted_at, price)");
        jdbcTemplate.execute("CREATE INDEX idx_product_brand_id_deleted_at_like_count ON product (brand_id, deleted_at, like_count)");
        jdbcTemplate.execute("ANALYZE TABLE product");
        log.info("인덱스 6개 추가 완료");
    }

    // ========================
    // 인덱스 추가 후 — shallow pagination
    // ========================

    @Test
    @Order(8)
    @DisplayName("[인덱스 추가 후 | 1페이지] latest 정렬")
    void withIndex_latest() {
        Map<String, Object> result = explain("인덱스 추가 후 | 1페이지 | latest", LATEST);
        assertThat(result.get("key")).isNotNull();
    }

    @Test
    @Order(9)
    @DisplayName("[인덱스 추가 후 | 1페이지] price_asc 정렬")
    void withIndex_priceAsc() {
        Map<String, Object> result = explain("인덱스 추가 후 | 1페이지 | price_asc", PRICE_ASC);
        assertThat(result.get("key")).isNotNull();
    }

    @Test
    @Order(10)
    @DisplayName("[인덱스 추가 후 | 1페이지] likes_desc 정렬")
    void withIndex_likesDesc() {
        Map<String, Object> result = explain("인덱스 추가 후 | 1페이지 | likes_desc", LIKES_DESC);
        assertThat(result.get("key")).isNotNull();
    }

    @Test
    @Order(11)
    @DisplayName("[인덱스 추가 후 | 1페이지] 브랜드 필터 + latest 정렬")
    void withIndex_brandFilter_latest() {
        Map<String, Object> result = explain("인덱스 추가 후 | 1페이지 | 브랜드 + latest", BRAND_LATEST, getFirstBrandId());
        assertThat(result.get("key")).isNotNull();
    }

    @Test
    @Order(12)
    @DisplayName("[인덱스 추가 후 | 1페이지] 브랜드 필터 + price_asc 정렬")
    void withIndex_brandFilter_priceAsc() {
        Map<String, Object> result = explain("인덱스 추가 후 | 1페이지 | 브랜드 + price_asc", BRAND_PRICE_ASC, getFirstBrandId());
        assertThat(result.get("key")).isNotNull();
    }

    @Test
    @Order(13)
    @DisplayName("[인덱스 추가 후 | 1페이지] 브랜드 필터 + likes_desc 정렬")
    void withIndex_brandFilter_likesDesc() {
        Map<String, Object> result = explain("인덱스 추가 후 | 1페이지 | 브랜드 + likes_desc", BRAND_LIKES_DESC, getFirstBrandId());
        assertThat(result.get("key")).isNotNull();
    }

    // ========================
    // 인덱스 추가 후 — deep pagination
    // ========================

    @Test
    @Order(14)
    @DisplayName("[인덱스 추가 후 | 깊은 페이지] latest 정렬 (OFFSET 40000)")
    void withIndex_deep_latest() {
        Map<String, Object> result = explain("인덱스 추가 후 | 깊은 페이지 | latest", DEEP_LATEST);
        assertThat(result.get("key")).isNotNull();
    }

    @Test
    @Order(15)
    @DisplayName("[인덱스 추가 후 | 깊은 페이지] likes_desc 정렬 (OFFSET 40000)")
    void withIndex_deep_likesDesc() {
        Map<String, Object> result = explain("인덱스 추가 후 | 깊은 페이지 | likes_desc", DEEP_LIKES_DESC);
        assertThat(result.get("key")).isNotNull();
    }

    @Test
    @Order(16)
    @DisplayName("[인덱스 추가 후 | 깊은 페이지] 브랜드 필터 + latest 정렬 (OFFSET 600)")
    void withIndex_deep_brandFilter_latest() {
        Map<String, Object> result = explain("인덱스 추가 후 | 깊은 페이지 | 브랜드 + latest", DEEP_BRAND_LATEST, getFirstBrandId());
        assertThat(result.get("key")).isNotNull();
    }

    @Test
    @Order(17)
    @DisplayName("[인덱스 추가 후 | 깊은 페이지] 브랜드 필터 + likes_desc 정렬 (OFFSET 600)")
    void withIndex_deep_brandFilter_likesDesc() {
        Map<String, Object> result = explain("인덱스 추가 후 | 깊은 페이지 | 브랜드 + likes_desc", DEEP_BRAND_LIKES_DESC, getFirstBrandId());
        assertThat(result.get("key")).isNotNull();
    }

    private Map<String, Object> explain(String label, String sql, Object... params) {
        Map<String, Object> row = jdbcTemplate.queryForList("EXPLAIN " + sql, params).getFirst();
        String analyze = jdbcTemplate.queryForObject("EXPLAIN ANALYZE " + sql, String.class, params);
        log.info("[{}] type={}, key={}, rows={}, Extra={}\n[ANALYZE]\n{}", label, row.get("type"), row.get("key"), row.get("rows"), row.get("Extra"), analyze);
        return row;
    }

    private long getFirstBrandId() {
        Long brandId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM brand", Long.class);
        assertThat(brandId).isNotNull();
        return brandId;
    }
}
