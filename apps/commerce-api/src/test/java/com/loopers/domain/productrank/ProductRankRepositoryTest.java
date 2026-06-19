package com.loopers.domain.productrank;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductRankRepositoryTest {

    @Autowired ProductRankRepository repository;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("키셋이 likes_desc 순서로 중복 없이 페이지를 넘긴다")
    @Test
    void keyset_returns_likes_desc_and_pages_without_dup() {
        repository.replaceAll(List.of(
            new ProductRank(1L, 7L, 50L),
            new ProductRank(2L, 7L, 30L),
            new ProductRank(3L, 7L, 30L),
            new ProductRank(4L, 7L, 10L)
        ));

        // 1페이지: 상위 2개 — 50(id1), 그다음 like 30 동률은 product_id DESC 라 id3 먼저
        List<Long> page1 = repository.findIdsByBrandLikesDesc(7L, null, null, 2);
        assertThat(page1).containsExactly(1L, 3L);

        // 2페이지: 커서=(30, 3) 다음부터
        List<Long> page2 = repository.findIdsByBrandLikesDesc(7L, 30L, 3L, 2);
        assertThat(page2).containsExactly(2L, 4L);
    }

    @DisplayName("brandId 가 null 이면 무필터 전체에서 정렬한다")
    @Test
    void null_brand_means_global() {
        repository.replaceAll(List.of(
            new ProductRank(1L, 7L, 5L),
            new ProductRank(2L, 8L, 99L)
        ));

        List<Long> top = repository.findIdsByBrandLikesDesc(null, null, null, 1);
        assertThat(top).containsExactly(2L);
    }
}
