package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RankingKeys 단위 테스트")
class RankingKeysTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 16);

    @Nested
    @DisplayName("키 포맷")
    class KeyFormat {

        @Test
        @DisplayName("raw 신호 보드 키는 ranking:{signal}:{yyyyMMdd} 형식이다")
        void rawKeys() {
            assertThat(RankingKeys.raw(RankingSignal.VIEW, DATE)).isEqualTo("ranking:view:20260716");
            assertThat(RankingKeys.raw(RankingSignal.LIKE, DATE)).isEqualTo("ranking:like:20260716");
            assertThat(RankingKeys.raw(RankingSignal.ORDER_COUNT, DATE)).isEqualTo("ranking:order_count:20260716");
            assertThat(RankingKeys.raw(RankingSignal.ORDER_QTY, DATE)).isEqualTo("ranking:order_qty:20260716");
        }

        @Test
        @DisplayName("display 보드 키는 ranking:all:{yyyyMMdd} 형식이다")
        void displayKey() {
            assertThat(RankingKeys.display(DATE)).isEqualTo("ranking:all:20260716");
        }

        @Test
        @DisplayName("carry-over 마커 키는 display 키 + :carryover 형식이다")
        void carryOverMarkerKey() {
            assertThat(RankingKeys.carryOverMarker(DATE)).isEqualTo("ranking:all:20260716:carryover");
        }
    }

    @Nested
    @DisplayName("버킷 귀속 (occurredAt → KST 달력 날짜)")
    class BucketOf {

        @Test
        @DisplayName("UTC 로 온 이벤트는 KST 로 번역해 귀속한다 — UTC 15일 15:30 = KST 16일 00:30 → 16일 버킷")
        void utcEventCrossesMidnightInKst() {
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 7, 15, 15, 30, 0, 0, ZoneOffset.UTC);

            assertThat(RankingKeys.bucketOf(occurredAt)).isEqualTo(LocalDate.of(2026, 7, 16));
        }

        @Test
        @DisplayName("UTC 15일 14:59 = KST 15일 23:59 → 자정 직전은 15일 버킷")
        void justBeforeKstMidnight() {
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 7, 15, 14, 59, 59, 0, ZoneOffset.UTC);

            assertThat(RankingKeys.bucketOf(occurredAt)).isEqualTo(LocalDate.of(2026, 7, 15));
        }

        @Test
        @DisplayName("KST 로 온 이벤트는 그 달력 날짜 그대로 귀속한다")
        void kstEventKeepsItsDate() {
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 7, 16, 0, 0, 1, 0, RankingKeys.ZONE);

            assertThat(RankingKeys.bucketOf(occurredAt)).isEqualTo(LocalDate.of(2026, 7, 16));
        }
    }
}
