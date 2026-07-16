package com.loopers.application.ranking;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RankingFacadeTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 15);

    @Autowired RankingFacade rankingFacade;
    @Autowired ProductRepository productRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired DatabaseCleanUp databaseCleanUp;
    @Autowired RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("랭킹 페이지는 점수 내림차순 + 상품정보(name/price) aggregation 으로 반환된다.")
    @Test
    void rankingAggregatesProductInfo() {
        Product p1 = productRepository.save(new Product(1L, "인기상품", "d", 5000L, 10));
        Product p2 = productRepository.save(new Product(1L, "차순위상품", "d", 3000L, 10));
        String key = RankingKeys.daily(DATE);
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 2.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 1.0);

        RankingPageInfo info = rankingFacade.getRankings("20260715", 1, 20);

        assertThat(info.totalCount()).isEqualTo(2L);
        assertThat(info.items()).hasSize(2);
        assertThat(info.items().get(0).rank()).isEqualTo(1L);
        assertThat(info.items().get(0).productId()).isEqualTo(p1.getId());
        assertThat(info.items().get(0).name()).isEqualTo("인기상품");
        assertThat(info.items().get(0).price()).isEqualTo(5000L);
        assertThat(info.items().get(1).rank()).isEqualTo(2L);
    }

    @DisplayName("2페이지의 rank 는 offset 이 반영된다 (size=1, page=2 → rank 2).")
    @Test
    void secondPageRankHasOffset() {
        Product p1 = productRepository.save(new Product(1L, "일등", "d", 1000L, 10));
        Product p2 = productRepository.save(new Product(1L, "이등", "d", 1000L, 10));
        String key = RankingKeys.daily(DATE);
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 2.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 1.0);

        RankingPageInfo info = rankingFacade.getRankings("20260715", 2, 1);

        assertThat(info.items()).hasSize(1);
        assertThat(info.items().get(0).rank()).isEqualTo(2L);
        assertThat(info.items().get(0).productId()).isEqualTo(p2.getId());
    }

    @DisplayName("DB 에 없는(삭제된) 상품은 목록에서 제외된다.")
    @Test
    void missingProductFiltered() {
        Product p1 = productRepository.save(new Product(1L, "실존상품", "d", 1000L, 10));
        String key = RankingKeys.daily(DATE);
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 2.0);
        redisTemplate.opsForZSet().add(key, "99999", 9.0); // DB 에 없는 상품이 1위

        RankingPageInfo info = rankingFacade.getRankings("20260715", 1, 20);

        assertThat(info.items()).hasSize(1);
        assertThat(info.items().get(0).productId()).isEqualTo(p1.getId());
    }

    @DisplayName("date 미지정이면 오늘(KST) 랭킹을 조회한다 — 빈 랭킹이면 빈 목록.")
    @Test
    void defaultDateIsToday() {
        RankingPageInfo info = rankingFacade.getRankings(null, 1, 20);

        assertThat(info.items()).isEmpty();
        assertThat(info.totalCount()).isZero();
    }

    @DisplayName("잘못된 date 형식·page<1·size<1 은 BAD_REQUEST 다.")
    @Test
    void invalidParamsRejected() {
        assertThatThrownBy(() -> rankingFacade.getRankings("2026-07-15", 1, 20))
            .isInstanceOf(CoreException.class)
            .extracting(e -> ((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThatThrownBy(() -> rankingFacade.getRankings("20260715", 0, 20))
            .isInstanceOf(CoreException.class);
        assertThatThrownBy(() -> rankingFacade.getRankings("20260715", 1, 0))
            .isInstanceOf(CoreException.class);
    }
}
