package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRank;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("RankingRedisRepository(commerce-api, 읽기) 통합 테스트")
class RankingRedisRepositoryIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 16);

    @Autowired
    private RankingRepository rankingRepository;

    // 시드는 master 로 넣는다(읽기 대상 데이터 준비). 조회 대상 어댑터는 기본(replica-preferred) 템플릿이지만
    // 테스트 컨테이너는 단일 노드라 같은 곳을 본다.
    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private void seed(long productId, double score) {
        redisTemplate.opsForZSet().add(RankingKeys.display(DATE), String.valueOf(productId), score);
    }

    @Nested
    @DisplayName("page")
    class Page {

        @Test
        @DisplayName("점수 내림차순으로, ZSET 순서 그대로 반환한다")
        void returnsInScoreDescendingOrder() {
            seed(1L, 1.0);
            seed(2L, 3.0);
            seed(3L, 2.0);

            List<RankedProduct> result = rankingRepository.page(DATE, 0, 10);

            assertThat(result).extracting(RankedProduct::productId).containsExactly(2L, 3L, 1L);
            assertThat(result.get(0).score()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("page/size 로 구간을 자른다 — 2페이지는 offset 이후 항목")
        void paginates() {
            seed(1L, 4.0);
            seed(2L, 3.0);
            seed(3L, 2.0);
            seed(4L, 1.0);

            List<RankedProduct> secondPage = rankingRepository.page(DATE, 1, 2);

            assertThat(secondPage).extracting(RankedProduct::productId).containsExactly(3L, 4L);
        }

        @Test
        @DisplayName("보드가 없는 날짜는 빈 목록이다")
        void missingBoardReturnsEmpty() {
            assertThat(rankingRepository.page(DATE.plusDays(10), 0, 10)).isEmpty();
            assertThat(rankingRepository.total(DATE.plusDays(10))).isZero();
        }
    }

    @Nested
    @DisplayName("rankOf")
    class RankOf {

        @Test
        @DisplayName("1-based 순위와 점수를 반환한다 (ZREVRANK 0-based 변환)")
        void returnsOneBasedRank() {
            seed(1L, 3.0);
            seed(2L, 2.0);
            seed(3L, 1.0);

            Optional<ProductRank> rank = rankingRepository.rankOf(DATE, 2L);

            assertThat(rank).isPresent();
            assertThat(rank.get().rank()).isEqualTo(2L);
            assertThat(rank.get().score()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("보드에 없는 상품은 empty — 오류가 아닌 정상 상태")
        void absentMemberReturnsEmpty() {
            seed(1L, 1.0);

            assertThat(rankingRepository.rankOf(DATE, 999L)).isEmpty();
        }
    }
}
