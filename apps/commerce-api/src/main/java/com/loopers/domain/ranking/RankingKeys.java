package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 일간 랭킹 키 계산 — commerce-streamer 의 com.loopers.domain.ranking.RankingKeys 와
 * 크로스-앱 계약이다(키 형식·타임존을 반드시 동일하게 유지할 것).
 */
public final class RankingKeys {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private RankingKeys() {}

    public static String daily(LocalDate date) {
        return "ranking:all:" + DAY.format(date);
    }
}
