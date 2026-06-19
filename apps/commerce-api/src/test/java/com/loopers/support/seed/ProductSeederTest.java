package com.loopers.support.seed;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductSeederTest {

    @Autowired ProductSeeder seeder;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    void seeds_products_and_like_counts() {
        seeder.seed(1_000, 50);

        Long products = jdbc.queryForObject("SELECT COUNT(*) FROM product", Long.class);
        Long counts = jdbc.queryForObject("SELECT COUNT(*) FROM product_like_count", Long.class);
        Long distinctBrands = jdbc.queryForObject("SELECT COUNT(DISTINCT brand_id) FROM product", Long.class);

        assertThat(products).isEqualTo(1_000);
        assertThat(counts).isEqualTo(1_000);
        assertThat(distinctBrands).isBetween(2L, 50L);
    }
}
