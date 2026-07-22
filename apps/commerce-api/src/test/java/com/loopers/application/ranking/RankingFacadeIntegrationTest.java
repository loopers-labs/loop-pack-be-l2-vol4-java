package com.loopers.application.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RankingFacadeIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2025, 9, 6);
    private static final RankingKey KEY = RankingKey.of(DATE);

    private final RankingFacade rankingFacade;
    private final ProductJpaRepository productJpaRepository;
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RankingFacadeIntegrationTest(
        RankingFacade rankingFacade,
        ProductJpaRepository productJpaRepository,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.rankingFacade = rankingFacade;
        this.productJpaRepository = productJpaRepository;
        this.masterRedisTemplate = masterRedisTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private ProductModel save(String name, long price) {
        return productJpaRepository.save(new ProductModel(name, name + " 설명", price, 1L));
    }

    private void seedScore(long productId, double score) {
        masterRedisTemplate.opsForZSet().add(KEY.value(), String.valueOf(productId), score);
    }

    @DisplayName("랭킹 페이지는 ZSET 점수 순서대로 상품정보를 붙여 1-based 순위로 반환한다.")
    @Test
    void hydratesProductsInRankingOrderWithOneBasedRank() {
        // given
        ProductModel first = save("1위상품", 3_000L);
        ProductModel second = save("2위상품", 2_000L);
        ProductModel third = save("3위상품", 1_000L);
        seedScore(first.getId(), 3.0);
        seedScore(second.getId(), 2.0);
        seedScore(third.getId(), 1.0);

        // when
        List<RankingInfo> page = rankingFacade.getRankingPage(DATE, 0, 10);

        // then
        assertThat(page).extracting(RankingInfo::rank).containsExactly(1L, 2L, 3L);
        assertThat(page).extracting(info -> info.product().id())
            .containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(page).extracting(info -> info.product().name())
            .containsExactly("1위상품", "2위상품", "3위상품");
        assertThat(page).extracting(RankingInfo::score).containsExactly(3.0, 2.0, 1.0);
    }

    @DisplayName("삭제된 상품은 응답에서 빠지되 남은 상품의 순위는 원래 순위를 유지한다.")
    @Test
    void skipsDeletedProductButKeepsOriginalRank() {
        // given
        ProductModel first = save("1위상품", 3_000L);
        ProductModel second = save("2위상품", 2_000L);
        ProductModel third = save("3위상품", 1_000L);
        second.delete();
        productJpaRepository.save(second);
        seedScore(first.getId(), 3.0);
        seedScore(second.getId(), 2.0);
        seedScore(third.getId(), 1.0);

        // when
        List<RankingInfo> page = rankingFacade.getRankingPage(DATE, 0, 10);

        // then
        assertThat(page).extracting(info -> info.product().id())
            .containsExactly(first.getId(), third.getId());
        assertThat(page).extracting(RankingInfo::rank).containsExactly(1L, 3L);
    }

    @DisplayName("다음 페이지의 순위는 앞 페이지를 이어 전역 1-based 순위로 매겨진다.")
    @Test
    void assignsGlobalRankAcrossPages() {
        // given
        ProductModel p1 = save("상품1", 5_000L);
        ProductModel p2 = save("상품2", 4_000L);
        ProductModel p3 = save("상품3", 3_000L);
        ProductModel p4 = save("상품4", 2_000L);
        seedScore(p1.getId(), 5.0);
        seedScore(p2.getId(), 4.0);
        seedScore(p3.getId(), 3.0);
        seedScore(p4.getId(), 2.0);

        // when
        List<RankingInfo> secondPage = rankingFacade.getRankingPage(DATE, 1, 2);

        // then
        assertThat(secondPage).extracting(RankingInfo::rank).containsExactly(3L, 4L);
        assertThat(secondPage).extracting(info -> info.product().id())
            .containsExactly(p3.getId(), p4.getId());
    }
}
