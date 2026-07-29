package com.loopers.ranking.interfaces.api;

import com.loopers.ranking.application.RankingQueryService;
import com.loopers.ranking.application.RankingResult;
import com.loopers.ranking.domain.RankingPeriod;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * period 파싱과 date 처리 분기가 컨트롤러의 책임이다 — 어느 서비스 경로로 가는지, 생략된 date 를 어떻게 넘기는지.
 */
class RankingV1ControllerTest {

    private final RankingQueryService service = mock(RankingQueryService.class);
    private final RankingV1Controller controller = new RankingV1Controller(service);

    private final RankingResult.Page empty = new RankingResult.Page(List.of(), 0, 1, 20, false);

    @DisplayName("period 를 생략하면 DAILY — date 생략 시 오늘로 ZSET 경로를 탄다")
    @Test
    void noPeriod_routesToDailyWithToday() {
        when(service.getRankingPage(any(), anyInt(), anyInt())).thenReturn(empty);

        controller.getRankings("DAILY", null, 1, 20);

        verify(service).getRankingPage(eq(LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))), eq(1), eq(20));
    }

    @DisplayName("period=WEEKLY 는 MV 경로로 가고, date 를 그대로 넘긴다")
    @Test
    void weekly_routesToMvWithDate() {
        when(service.getPeriodRankingPage(any(), any(), anyInt(), anyInt())).thenReturn(empty);

        controller.getRankings("WEEKLY", "20260722", 1, 20);

        verify(service).getPeriodRankingPage(eq(RankingPeriod.WEEKLY), eq(LocalDate.of(2026, 7, 22)), eq(1), eq(20));
    }

    @DisplayName("period=MONTHLY 이고 date 를 생략하면 null 을 넘겨 서비스가 최근 확정본을 고르게 한다")
    @Test
    void monthlyNoDate_passesNull() {
        when(service.getPeriodRankingPage(any(), isNull(), anyInt(), anyInt())).thenReturn(empty);

        controller.getRankings("MONTHLY", null, 1, 20);

        verify(service).getPeriodRankingPage(eq(RankingPeriod.MONTHLY), isNull(), eq(1), eq(20));
    }

    @DisplayName("소문자 period 도 받아들인다")
    @Test
    void lowercasePeriod_accepted() {
        when(service.getPeriodRankingPage(any(), any(), anyInt(), anyInt())).thenReturn(empty);

        controller.getRankings("weekly", "20260722", 1, 20);

        verify(service).getPeriodRankingPage(eq(RankingPeriod.WEEKLY), any(), anyInt(), anyInt());
    }

    @DisplayName("알 수 없는 period 는 400 이다")
    @Test
    void unknownPeriod_throwsBadRequest() {
        assertThatThrownBy(() -> controller.getRankings("YEARLY", null, 1, 20))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @DisplayName("날짜 형식이 틀리면 400 이다")
    @Test
    void malformedDate_throwsBadRequest() {
        assertThatThrownBy(() -> controller.getRankings("WEEKLY", "2026-07-22", 1, 20))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }
}
