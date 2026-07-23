package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RankingCarryOverSchedulerTest {

    private RankingCarryOverScheduler scheduler;

    @Mock private RankingRepository rankingRepository;

    @BeforeEach
    void setUp() {
        scheduler = new RankingCarryOverScheduler(rankingRepository);
    }

    @DisplayName("오늘 랭킹 상위 항목이 감쇠 계수(0.3)를 곱한 점수로 내일 키에 이월된다.")
    @Test
    void carriesOverTopEntries_withDecayedScore() {
        // arrange
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        given(rankingRepository.findTopEntries(today, RankingCarryOverScheduler.CARRY_OVER_LIMIT))
            .willReturn(List.of(new RankingEntry(1L, 6_000.0), new RankingEntry(2L, 0.6)));

        // act
        scheduler.carryOver();

        // assert — 6000 × 0.3 = 1800, 0.6 × 0.3 = 0.18
        then(rankingRepository).should().saveScoreIfAbsent(tomorrow, 1L, 1_800.0);
        then(rankingRepository).should().saveScoreIfAbsent(tomorrow, 2L, 0.6 * 0.3);
    }

    @DisplayName("오늘 랭킹판이 비어 있으면 이월하지 않는다.")
    @Test
    void skipsCarryOver_whenTodayRankingIsEmpty() {
        // arrange
        given(rankingRepository.findTopEntries(any(), anyInt())).willReturn(List.of());

        // act
        scheduler.carryOver();

        // assert
        then(rankingRepository).should(never()).saveScoreIfAbsent(any(), anyLong(), anyDouble());
    }

    @DisplayName("이월 중 Redis 예외가 발생해도 전파하지 않는다 — 스케줄러가 죽지 않는다.")
    @Test
    void swallowsException_whenRedisFails() {
        // arrange
        given(rankingRepository.findTopEntries(any(), anyInt()))
            .willReturn(List.of(new RankingEntry(1L, 1.0)));
        willThrow(new RuntimeException("redis down"))
            .given(rankingRepository).saveScoreIfAbsent(any(), eq(1L), anyDouble());

        // act & assert
        assertThatCode(() -> scheduler.carryOver()).doesNotThrowAnyException();
    }
}
