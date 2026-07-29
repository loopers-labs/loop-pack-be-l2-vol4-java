package com.loopers.ranking.application;

import com.loopers.product.application.ProductInfo;
import com.loopers.product.application.ProductReader;
import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingMvRepository;
import com.loopers.ranking.domain.RankingPeriod;
import com.loopers.ranking.domain.RankingRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.RecoverableDataAccessException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankingQueryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingRepository rankingRepository = mock(RankingRepository.class);
    private final RankingMvRepository rankingMvRepository = mock(RankingMvRepository.class);
    private final ProductReader productReader = mock(ProductReader.class);
    private final RankingQueryService service =
            new RankingQueryService(rankingRepository, rankingMvRepository, productReader);

    private final LocalDate today = LocalDate.now(SEOUL);

    private ProductInfo info(String name) {
        return new ProductInfo(name, 1L, 1000L);
    }

    @DisplayName("창 안이면 ZSET 을 읽어 순위·점수·상품정보를 조합한다")
    @Test
    void servable_returnsRankedItems() {
        when(rankingRepository.range(any(), anyLong(), anyLong()))
                .thenReturn(List.of(new RankingEntry(101L, 5.0), new RankingEntry(202L, 3.0)));
        when(rankingRepository.size(any())).thenReturn(2L);
        when(productReader.getInfos(anyList()))
                .thenReturn(Map.of(101L, info("A"), 202L, info("B")));

        RankingResult.Page page = service.getRankingPage(today, 1, 20);

        assertThat(page.degraded()).isFalse();
        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.items()).extracting(RankingResult.Item::rank).containsExactly(1L, 2L);
        assertThat(page.items()).extracting(RankingResult.Item::productId).containsExactly(101L, 202L);
        assertThat(page.items().get(0).score()).isCloseTo(5.0, within(1e-9));
    }

    @DisplayName("서빙 창 밖(2일 전) 조회는 RANKING_NOT_AVAILABLE(NOT_FOUND) 을 던진다")
    @Test
    void notServable_throwsNotFound() {
        assertThatThrownBy(() -> service.getRankingPage(today.minusDays(2), 1, 20))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }

    @DisplayName("창 안이지만 데이터가 없으면(콜드스타트) 200 빈 목록을 준다")
    @Test
    void coldStart_returnsEmptyPage() {
        when(rankingRepository.range(any(), anyLong(), anyLong())).thenReturn(List.of());
        when(rankingRepository.size(any())).thenReturn(0L);

        RankingResult.Page page = service.getRankingPage(today, 1, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.degraded()).isFalse();
    }

    @DisplayName("Redis 장애 시 좋아요순(DB) 폴백으로 degraded 응답을 준다")
    @Test
    void redisDown_fallsBackToLikesDegraded() {
        when(rankingRepository.range(any(), anyLong(), anyLong()))
                .thenThrow(new RecoverableDataAccessException("redis down"));
        LinkedHashMap<Long, ProductInfo> top = new LinkedHashMap<>();
        top.put(101L, info("A"));
        top.put(202L, info("B"));
        when(productReader.getTopByLikes(anyInt())).thenReturn(top);

        RankingResult.Page page = service.getRankingPage(today, 1, 20);

        assertThat(page.degraded()).isTrue();
        assertThat(page.items()).extracting(RankingResult.Item::productId).containsExactly(101L, 202L);
        assertThat(page.items()).extracting(RankingResult.Item::score).containsOnlyNulls();
    }

    @Nested
    @DisplayName("주간·월간(MV)")
    class Period {

        @DisplayName("date 가 속한 완결 기간의 MV 를 읽어 순위를 조합한다")
        @Test
        void completedPeriod_returnsRankedItems() {
            LocalDate lastWeekDay = today.minusWeeks(1);
            String key = RankingPeriod.WEEKLY.periodKey(lastWeekDay);
            when(rankingMvRepository.count(eq(RankingPeriod.WEEKLY), eq(key))).thenReturn(2L);
            when(rankingMvRepository.findPage(eq(RankingPeriod.WEEKLY), eq(key), anyLong(), anyInt()))
                    .thenReturn(List.of(new RankingEntry(101L, 9.0), new RankingEntry(202L, 3.0)));
            when(productReader.getInfos(anyList())).thenReturn(Map.of(101L, info("A"), 202L, info("B")));

            RankingResult.Page page = service.getPeriodRankingPage(RankingPeriod.WEEKLY, lastWeekDay, 1, 20);

            assertThat(page.degraded()).isFalse();
            assertThat(page.total()).isEqualTo(2L);
            assertThat(page.items()).extracting(RankingResult.Item::rank).containsExactly(1L, 2L);
            assertThat(page.items()).extracting(RankingResult.Item::productId).containsExactly(101L, 202L);
        }

        @DisplayName("date 를 생략하면 가장 최근 확정본을 읽는다")
        @Test
        void noDate_readsLatestCompleted() {
            String key = RankingPeriod.WEEKLY.latestCompletedKey(today);
            when(rankingMvRepository.count(eq(RankingPeriod.WEEKLY), eq(key))).thenReturn(1L);
            when(rankingMvRepository.findPage(eq(RankingPeriod.WEEKLY), eq(key), anyLong(), anyInt()))
                    .thenReturn(List.of(new RankingEntry(101L, 9.0)));
            when(productReader.getInfos(anyList())).thenReturn(Map.of(101L, info("A")));

            RankingResult.Page page = service.getPeriodRankingPage(RankingPeriod.WEEKLY, null, 1, 20);

            assertThat(page.items()).extracting(RankingResult.Item::productId).containsExactly(101L);
        }

        @DisplayName("진행 중인 기간(아직 안 끝남)은 MV 를 읽지 않고 404 다")
        @Test
        void inProgressPeriod_throwsNotFound() {
            assertThatThrownBy(() -> service.getPeriodRankingPage(RankingPeriod.WEEKLY, today, 1, 20))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("완결이지만 배치가 안 돌아 데이터가 없으면 404 다")
        @Test
        void completedButEmpty_throwsNotFound() {
            LocalDate lastWeekDay = today.minusWeeks(1);
            String key = RankingPeriod.WEEKLY.periodKey(lastWeekDay);
            when(rankingMvRepository.count(eq(RankingPeriod.WEEKLY), eq(key))).thenReturn(0L);

            assertThatThrownBy(() -> service.getPeriodRankingPage(RankingPeriod.WEEKLY, lastWeekDay, 1, 20))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}
