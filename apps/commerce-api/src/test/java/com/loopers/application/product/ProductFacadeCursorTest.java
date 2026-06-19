package com.loopers.application.product;

import com.loopers.domain.productrank.ProductRank;
import com.loopers.domain.productrank.ProductRankRepository;
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
class ProductFacadeCursorTest {

    @Autowired ProductFacade facade;
    @Autowired ProductRankRepository rankRepository;
    @Autowired ProductSeeder seeder;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
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
}
