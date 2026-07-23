package com.loopers.tddstudy.application.ranking;

import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.domain.product.ProductRepository;
import com.loopers.tddstudy.domain.ranking.RankingPeriodType;
import com.loopers.tddstudy.support.FakePeriodRankingRepository;
import com.loopers.tddstudy.support.FakeRankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RankingServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 14);

    private FakeRankingRepository rankingRepository;
    private FakePeriodRankingRepository periodRankingRepository;
    private ProductRepository productRepository;
    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        rankingRepository = new FakeRankingRepository();
        periodRankingRepository = new FakePeriodRankingRepository();
        productRepository = mock(ProductRepository.class);
        rankingService = new RankingService(
                rankingRepository, periodRankingRepository, productRepository);
    }

    @Test
    @DisplayName("랭킹 페이지는 점수순으로 상품정보와 순위가 합쳐져 반환된다")
    void page_aggregates_product_info() {
        rankingRepository.incrementScore(DATE, 101L, 0.7);   // 1등
        rankingRepository.incrementScore(DATE, 202L, 0.4);   // 2등
        when(productRepository.findById(101L))
                .thenReturn(Optional.of(new Product("운동화", 50000, 10, 1L)));
        when(productRepository.findById(202L))
                .thenReturn(Optional.of(new Product("양말", 3000, 99, 1L)));

        List<RankingInfo> page = rankingService.getRankingPage(DATE, 1, 20);

        assertThat(page).containsExactly(
                new RankingInfo(1, 101L, "운동화", 50000, 0.7),
                new RankingInfo(2, 202L, "양말", 3000, 0.4)
        );
    }

    @Test
    @DisplayName("DB에 없는 상품은 목록에서 건너뛰되 순위 번호는 유지된다")
    void skips_missing_product_keeping_rank_numbers() {
        rankingRepository.incrementScore(DATE, 101L, 0.9);   // 1등
        rankingRepository.incrementScore(DATE, 999L, 0.5);   // 2등 — DB엔 없음(삭제됨)
        rankingRepository.incrementScore(DATE, 202L, 0.2);   // 3등
        when(productRepository.findById(101L))
                .thenReturn(Optional.of(new Product("운동화", 50000, 10, 1L)));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());
        when(productRepository.findById(202L))
                .thenReturn(Optional.of(new Product("양말", 3000, 99, 1L)));

        List<RankingInfo> page = rankingService.getRankingPage(DATE, 1, 20);

        assertThat(page).containsExactly(
                new RankingInfo(1, 101L, "운동화", 50000, 0.9),
                new RankingInfo(3, 202L, "양말", 3000, 0.2)   // 2등이 빠져도 3등은 3등
        );
    }

    @Test
    @DisplayName("2페이지의 순위 번호는 이어서 매겨진다")
    void second_page_rank_continues() {
        rankingRepository.incrementScore(DATE, 101L, 0.9);
        rankingRepository.incrementScore(DATE, 202L, 0.5);
        rankingRepository.incrementScore(DATE, 303L, 0.2);
        when(productRepository.findById(303L))
                .thenReturn(Optional.of(new Product("모자", 12000, 5, 1L)));

        List<RankingInfo> page = rankingService.getRankingPage(DATE, 2, 2);  // size=2의 2페이지

        assertThat(page).containsExactly(
                new RankingInfo(3, 303L, "모자", 12000, 0.2)   // 21이 아니라 3 (size=2 기준)
        );
    }

    @Test
    @DisplayName("상품의 순위는 1-based로 반환된다")
    void getRank_isOneBased() {
        rankingRepository.incrementScore(DATE, 101L, 0.7);   // 1등 (0-based 0)
        rankingRepository.incrementScore(DATE, 202L, 0.4);   // 2등 (0-based 1)

        assertThat(rankingService.getRank(DATE, 101L)).isEqualTo(1L);
        assertThat(rankingService.getRank(DATE, 202L)).isEqualTo(2L);
    }

    @Test
    @DisplayName("랭킹에 없는 상품의 순위는 null 이다")
    void getRank_isNullWhenAbsent() {
        rankingRepository.incrementScore(DATE, 101L, 0.7);

        assertThat(rankingService.getRank(DATE, 999L)).isNull();
    }

    @Test
    @DisplayName("주간 랭킹은 직전에 끝난 주의 MV에서 조회된다")
    void weekly_reads_last_completed_week() {
        // 2026-07-06(월) 기준 → 직전 주는 2026-W27 (6/29~7/5)
        periodRankingRepository.addWeekly("2026-W27", 101L, 9.0);
        when(productRepository.findById(101L))
                .thenReturn(Optional.of(new Product("운동화", 50000, 10, 1L)));

        List<RankingInfo> page = rankingService.getRankingPage(
                RankingPeriodType.WEEKLY, LocalDate.of(2026, 7, 6), 1, 20);

        assertThat(page).containsExactly(new RankingInfo(1, 101L, "운동화", 50000, 9.0));
    }

    @Test
    @DisplayName("월간 랭킹은 직전에 끝난 달의 MV에서 조회된다")
    void monthly_reads_last_completed_month() {
        // 2026-08-01 기준 → 직전 달은 2026-07
        periodRankingRepository.addMonthly("2026-07", 101L, 9.0);
        when(productRepository.findById(101L))
                .thenReturn(Optional.of(new Product("운동화", 50000, 10, 1L)));

        List<RankingInfo> page = rankingService.getRankingPage(
                RankingPeriodType.MONTHLY, LocalDate.of(2026, 8, 1), 1, 20);

        assertThat(page).containsExactly(new RankingInfo(1, 101L, "운동화", 50000, 9.0));
    }

    @Test
    @DisplayName("아직 집계되지 않은 기간은 빈 목록을 반환한다")
    void returns_empty_when_period_not_aggregated() {
        List<RankingInfo> page = rankingService.getRankingPage(
                RankingPeriodType.WEEKLY, LocalDate.of(2026, 7, 6), 1, 20);

        assertThat(page).isEmpty();
    }

    @Test
    @DisplayName("일간 랭킹은 기존과 동일하게 Redis 기반으로 동작한다")
    void daily_still_uses_ranking_repository() {
        rankingRepository.incrementScore(DATE, 101L, 0.7);
        when(productRepository.findById(101L))
                .thenReturn(Optional.of(new Product("운동화", 50000, 10, 1L)));

        List<RankingInfo> page = rankingService.getRankingPage(
                RankingPeriodType.DAILY, DATE, 1, 20);

        assertThat(page).containsExactly(new RankingInfo(1, 101L, "운동화", 50000, 0.7));
    }
}
