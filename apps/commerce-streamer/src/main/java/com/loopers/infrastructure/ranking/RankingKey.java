package com.loopers.infrastructure.ranking;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 실시간 랭킹 ZSET 의 키 계약. commerce-api 의 읽기 측({@code infrastructure.ranking.RankingKey})과
 * <b>키 포맷·TTL 이 반드시 동일</b>해야 한다(앱 경계라 코드 공유 없이 문자열 계약만 맞춘다 — product_metrics 를
 * streamer 가 직접 UPDATE 하는 것과 같은 컨벤션).
 *
 * <ul>
 *   <li>KEY : {@code ranking:all:{yyyyMMdd}} (일간, KST 기준)</li>
 *   <li>TTL : 2 Day</li>
 * </ul>
 */
public final class RankingKey {

    /** 일간 버킷 기준 시간대(KST). 이벤트 발생시각(UTC 저장)을 이 존으로 환산해 일자를 정한다. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** ZSET TTL — 2일. 어제/오늘 두 개의 일간 랭킹을 조회 가능하게 유지한다. */
    public static final Duration TTL = Duration.ofDays(2);

    private static final String PREFIX = "ranking:all:";
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private RankingKey() {
    }

    /** {@code ranking:all:20260714} 형태의 일간 키. */
    public static String daily(LocalDate date) {
        return PREFIX + date.format(YYYYMMDD);
    }

    /**
     * 이벤트 발생시각(ISO-8601 문자열, 발행측은 {@code ISO_OFFSET_DATE_TIME})을 KST 일자로 환산한다.
     * 파싱 실패/누락 시 현재 KST 일자로 폴백해 유실 없이 오늘 버킷에 반영한다.
     */
    public static LocalDate dateOf(String occurredAt) {
        if (occurredAt != null && !occurredAt.isBlank()) {
            try {
                return OffsetDateTime.parse(occurredAt).atZoneSameInstant(ZONE).toLocalDate();
            } catch (DateTimeParseException ignore) {
                try {
                    return Instant.parse(occurredAt).atZone(ZONE).toLocalDate();
                } catch (DateTimeParseException ignore2) {
                    // 알 수 없는 포맷 — 폴백
                }
            }
        }
        return LocalDate.now(ZONE);
    }
}
