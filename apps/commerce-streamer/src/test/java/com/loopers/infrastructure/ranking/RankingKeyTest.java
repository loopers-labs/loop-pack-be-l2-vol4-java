package com.loopers.infrastructure.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 랭킹 키/TTL/일자 환산 단위 테스트 — ZSET 키 계약(쓰기 측)을 검증한다.
 */
class RankingKeyTest {

    @DisplayName("daily 는 ranking:all:{yyyyMMdd} 형태의 키를 만든다.")
    @Test
    void daily() {
        assertThat(RankingKey.daily(LocalDate.of(2026, 7, 14))).isEqualTo("ranking:all:20260714");
    }

    @DisplayName("TTL 은 2일이다.")
    @Test
    void ttl() {
        assertThat(RankingKey.TTL).isEqualTo(Duration.ofDays(2));
    }

    @DisplayName("occurredAt(UTC)을 KST 일자로 환산한다 — 자정 근처는 다음 날 버킷.")
    @Test
    void dateOf_utc를_kst일자로() {
        // 2026-07-13 15:30 UTC + 9h = 2026-07-14 00:30 KST
        assertThat(RankingKey.dateOf("2026-07-13T15:30:00Z")).isEqualTo(LocalDate.of(2026, 7, 14));
        // 2026-07-14 23:00 UTC + 9h = 2026-07-15 08:00 KST
        assertThat(RankingKey.dateOf("2026-07-14T23:00:00Z")).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @DisplayName("offset 표기(+09:00) occurredAt 도 KST 일자로 환산한다.")
    @Test
    void dateOf_offset표기() {
        assertThat(RankingKey.dateOf("2026-07-14T00:00:00+09:00")).isEqualTo(LocalDate.of(2026, 7, 14));
    }

    @DisplayName("occurredAt 이 null/빈값/파싱불가면 현재 KST 일자로 폴백한다(유실 방지).")
    @Test
    void dateOf_폴백() {
        LocalDate today = LocalDate.now(RankingKey.ZONE);
        assertThat(RankingKey.dateOf(null)).isEqualTo(today);
        assertThat(RankingKey.dateOf("")).isEqualTo(today);
        assertThat(RankingKey.dateOf("not-a-date")).isEqualTo(today);
    }
}
