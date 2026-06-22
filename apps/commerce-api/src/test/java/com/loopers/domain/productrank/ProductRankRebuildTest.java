package com.loopers.domain.productrank;

import com.loopers.support.seed.ProductSeeder;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductRankRebuildTest {

    @Autowired ProductRankRepository repository;
    @Autowired ProductSeeder seeder;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private List<Long> ids(List<RankedProduct> ranked) {
        return ranked.stream().map(RankedProduct::productId).toList();
    }

    @DisplayName("재집계는 source 의 절대값 like_count 로 rank 를 다시 맞춘다")
    @Test
    void rebuild_uses_absolute_source_count() {
        seeder.seed(100, 5);
        jdbc.update("UPDATE product_like_count SET like_count = 1000000 WHERE product_id = 1");

        repository.rebuildFromSource();

        List<RankedProduct> top = repository.findRankedByBrandLikesDesc(null, null, null, 1);
        assertThat(ids(top)).containsExactly(1L);
        assertThat(top.get(0).likeCount()).isEqualTo(1000000L);
    }

    @DisplayName("재집계는 소프트 삭제된 상품을 제외한다")
    @Test
    void rebuild_excludes_soft_deleted_products() {
        seeder.seed(10, 2);
        jdbc.update("UPDATE product SET deleted_at = NOW() WHERE id = 1");

        repository.rebuildFromSource();

        List<RankedProduct> all = repository.findRankedByBrandLikesDesc(null, null, null, 100);
        assertThat(ids(all)).doesNotContain(1L);
    }
}
