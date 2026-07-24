package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RankingKeyTest {

    @Test
    @DisplayName("LocalDate 로 일간 랭킹 키를 만든다: ranking:all:yyyyMMdd")
    void daily_buildsKeyFromLocalDate() {
        String key = RankingKey.daily(LocalDate.of(2026, 7, 13));

        assertThat(key).isEqualTo("ranking:all:20260713");
    }

    @Test
    @DisplayName("KST 자정 직후(00:30) 이벤트는 UTC 로 전날이어도 그날(KST) 키로 계산된다")
    void dailyOf_usesKstDateBoundary() {
        // 2026-07-13 00:30 KST == 2026-07-12 15:30 UTC
        // 서버가 UTC 라도 KST 기준 날짜(07-13)로 키가 나와야 한다.
        Instant kstJustAfterMidnight = LocalDateTime.of(2026, 7, 13, 0, 30)
                .atZone(RankingKey.KST)
                .toInstant();

        String key = RankingKey.dailyOf(kstJustAfterMidnight);

        assertThat(key).isEqualTo("ranking:all:20260713");
    }
}
