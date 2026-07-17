package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 랭킹 Redis 키 체계. 일별 키 분리(시간의 양자화)로 리셋·만료·보정을 키 단위 운영으로 만든다.
 *
 * <p>display 세그먼트 {@code all} 은 세그먼트 확장 슬롯 — 브랜드/카테고리별 보드가 필요해지면
 * 같은 자리에 세그먼트 키를 추가하는 것으로 열린다(구조 변경 없음).</p>
 */
public final class RankingKeys {

    /** 버킷 귀속 기준 타임존. 이벤트 occurredAt 이 어떤 존으로 오든 이 존의 달력 날짜로 귀속한다. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final String PREFIX = "ranking";
    private static final String DISPLAY_SEGMENT = "all";

    private RankingKeys() {
    }

    /** 이벤트 발생 시각(occurredAt) → 귀속 날짜 버킷. 처리 시각이 아닌 발생 시각 기준(자정 경계 지연 도착 대비). */
    public static LocalDate bucketOf(ZonedDateTime occurredAt) {
        return occurredAt.withZoneSameInstant(ZONE).toLocalDate();
    }

    /** raw 신호 보드 키. 예: {@code ranking:view:20260716} */
    public static String raw(RankingSignal signal, LocalDate date) {
        return PREFIX + ":" + signal.segment() + ":" + DATE_FORMAT.format(date);
    }

    /** 서빙용 합성 보드 키. 예: {@code ranking:all:20260716} */
    public static String display(LocalDate date) {
        return PREFIX + ":" + DISPLAY_SEGMENT + ":" + DATE_FORMAT.format(date);
    }

    /** carry-over 1회 실행 가드 마커 키. 예: {@code ranking:all:20260716:carryover} */
    public static String carryOverMarker(LocalDate date) {
        return display(date) + ":carryover";
    }
}
