package com.loopers.application.product;

import com.loopers.domain.productrank.ProductRank;
import com.loopers.domain.productrank.ProductRankRepository;
import com.loopers.support.seed.ProductSeeder;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductFacadeCursorTest {

    @Autowired ProductFacade facade;
    @Autowired ProductRankRepository rankRepository;
    @Autowired ProductSeeder seeder;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatabaseCleanUp databaseCleanUp;
    @Autowired RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("커서 페이지는 rank 에 삭제 상품이 섞여도 over-fetch+필터로 size 를 채운다")
    @Test
    void cursor_page_fills_size_even_when_rank_has_deleted_product() {
        seeder.seed(50, 3);
        rankRepository.replaceAll(List.of(
            new ProductRank(1L, 1L, 50L),
            new ProductRank(2L, 1L, 40L),
            new ProductRank(3L, 1L, 30L),
            new ProductRank(4L, 1L, 20L),
            new ProductRank(5L, 1L, 10L)
        ));
        jdbc.update("UPDATE product SET deleted_at = NOW() WHERE id = 2"); // rank엔 남았지만 삭제됨

        ProductCursorPage page = facade.getProductsByLikesCursor(1L, null, 3);

        // 삭제된 2 를 건너뛰고 1,3,4 로 3개 채움
        assertThat(page.items()).extracting(ProductInfo::id).containsExactly(1L, 3L, 4L);
        assertThat(page.nextCursor()).isNotNull();
    }

    @DisplayName("다음 커서로 이어가면 중복 없이 다음 페이지가 나온다")
    @Test
    void next_cursor_continues_without_dup() {
        seeder.seed(50, 3);
        rankRepository.replaceAll(List.of(
            new ProductRank(1L, 1L, 50L),
            new ProductRank(2L, 1L, 40L),
            new ProductRank(3L, 1L, 30L),
            new ProductRank(4L, 1L, 20L)
        ));

        ProductCursorPage page1 = facade.getProductsByLikesCursor(1L, null, 2);
        assertThat(page1.items()).extracting(ProductInfo::id).containsExactly(1L, 2L);

        ProductCursorPage page2 = facade.getProductsByLikesCursor(1L, page1.nextCursor(), 2);
        assertThat(page2.items()).extracting(ProductInfo::id).containsExactly(3L, 4L);
    }

    @DisplayName("첫 페이지는 top-N 블롭 캐시를 타서, rank 가 바뀌어도 TTL 내엔 stale 정렬을 반환한다")
    @Test
    void first_page_uses_list_blob_cache() {
        seeder.seed(30, 2);
        rankRepository.replaceAll(List.of(
            new ProductRank(1L, 1L, 50L),
            new ProductRank(2L, 1L, 40L),
            new ProductRank(3L, 1L, 30L)
        ));

        ProductCursorPage first = facade.getProductsByLikesCursor(1L, null, 2);
        assertThat(first.items()).extracting(ProductInfo::id).containsExactly(1L, 2L); // 블롭 적재

        // rank 직접 변경: 3 을 1위로 — 블롭(TTL 내)이면 반영 안 됨
        jdbc.update("UPDATE product_rank SET like_count = 999 WHERE product_id = 3");

        ProductCursorPage cached = facade.getProductsByLikesCursor(1L, null, 2);
        assertThat(cached.items()).extracting(ProductInfo::id).containsExactly(1L, 2L); // stale = 블롭 히트 증명
    }

    @DisplayName("over-fetch 윈도가 꽉 찬 채 대량 삭제로 미달이어도, 다음 커서를 줘 뒤 페이지를 잃지 않는다")
    @Test
    void full_window_under_filled_still_emits_next_cursor() {
        seeder.seed(20, 1);
        List<ProductRank> ranks = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            ranks.add(new ProductRank(i, 1L, 100 - i)); // 1(99) > 2(98) > ... > 10(90)
        }
        rankRepository.replaceAll(ranks);

        // 1페이지로 커서 확보(블롭 경로) → [1,2]
        ProductCursorPage page1 = facade.getProductsByLikesCursor(1L, null, 2);
        assertThat(page1.items()).extracting(ProductInfo::id).containsExactly(1L, 2L);

        // 2페이지 윈도[3,4,5,6](need=4, 꽉 참) 중 3,4,5 삭제 → 6만 남아 size(2) 미달
        jdbc.update("UPDATE product SET deleted_at = NOW() WHERE id IN (3, 4, 5)");
        ProductCursorPage page2 = facade.getProductsByLikesCursor(1L, page1.nextCursor(), 2);

        assertThat(page2.items()).extracting(ProductInfo::id).containsExactly(6L); // 미달이지만
        assertThat(page2.nextCursor()).isNotNull(); // 7~10 을 잃지 않도록 다음 커서 발급
    }
}
