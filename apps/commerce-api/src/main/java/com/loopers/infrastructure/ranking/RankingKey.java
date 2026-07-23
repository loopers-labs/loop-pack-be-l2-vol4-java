package com.loopers.infrastructure.ranking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 실시간 랭킹 ZSET 의 키 계약(읽기 측). commerce-streamer 의 쓰기 측
 * ({@code infrastructure.ranking.RankingKey})과 <b>키 포맷이 반드시 동일</b>해야 한다
 * (앱 경계라 코드 공유 없이 문자열 계약만 맞춘다).
 *
 * <p>KEY : {@code ranking:all:{yyyyMMdd}} (일간, KST 기준)
 */
public final class RankingKey {

    /** 일간 버킷 기준 시간대(KST). "오늘" 랭킹 판단에 쓴다. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private static final String PREFIX = "ranking:all:";
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private RankingKey() {
    }

    /** {@code ranking:all:20260714} 형태의 일간 키. */
    public static String daily(LocalDate date) {
        return PREFIX + date.format(YYYYMMDD);
    }

    /** 오늘(KST) 일간 키 — 상품 상세의 실시간 랭킹 조회용. */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
