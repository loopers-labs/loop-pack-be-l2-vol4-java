package com.loopers.ranking.domain;

import com.loopers.ranking.domain.RankingWindow.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RankingWindowTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final LocalDate today = LocalDate.of(2026, 7, 17);

    @DisplayName("오늘 발생한 이벤트는 오늘 판(IN_WINDOW)이다")
    @Test
    void today_isInWindow() {
        var c = RankingWindow.classify(ZonedDateTime.of(2026, 7, 17, 9, 0, 0, 0, SEOUL), today);
        assertThat(c.verdict()).isEqualTo(Verdict.IN_WINDOW);
        assertThat(c.date()).isEqualTo(LocalDate.of(2026, 7, 17));
    }

    @DisplayName("자정 직전(어제 23:59)에 발생했으면 자정을 넘겨 처리돼도 어제 판이다")
    @Test
    void justBeforeMidnight_isYesterdayWindow() {
        var c = RankingWindow.classify(ZonedDateTime.of(2026, 7, 16, 23, 59, 0, 0, SEOUL), today);
        assertThat(c.verdict()).isEqualTo(Verdict.IN_WINDOW);
        assertThat(c.date()).isEqualTo(LocalDate.of(2026, 7, 16));
    }

    @DisplayName("내일 날짜는 미래(FUTURE)로 판정한다 — producer 시계 오차")
    @Test
    void tomorrow_isFuture() {
        var c = RankingWindow.classify(ZonedDateTime.of(2026, 7, 18, 0, 1, 0, 0, SEOUL), today);
        assertThat(c.verdict()).isEqualTo(Verdict.FUTURE);
    }

    @DisplayName("어제보다 이전(2일 전)은 너무 과거(TOO_OLD)로 판정한다")
    @Test
    void twoDaysAgo_isTooOld() {
        var c = RankingWindow.classify(ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, SEOUL), today);
        assertThat(c.verdict()).isEqualTo(Verdict.TOO_OLD);
    }

    @DisplayName("발생시각이 없으면 UNDATED 로 판정한다 — 터지지 않고 값으로 돌려준다")
    @Test
    void givenNullOccurredAt_whenClassify_thenUndated() {
        var c = RankingWindow.classify(null, today);

        assertThat(c.verdict()).isEqualTo(Verdict.UNDATED);
        assertThat(c.date()).isNull();
    }
}
