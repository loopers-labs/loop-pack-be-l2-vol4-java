package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 랭킹 display 보드 키 체계 (읽기 전용 — 쓰기 주체는 commerce-streamer).
 *
 * <p>streamer 의 RankingKeys 와 의도적 중복(~5줄): 두 앱이 공유하는 건 키 문자열 계약뿐이라
 * 공유 모듈 신설은 과설계로 기각. 양쪽 테스트가 키 리터럴을 핀해 drift 를 잡는다.</p>
 */
public final class RankingKeys {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private RankingKeys() {
    }

    /** 서빙용 합성 보드 키. 예: {@code ranking:all:20260716} */
    public static String display(LocalDate date) {
        return "ranking:all:" + DATE_FORMAT.format(date);
    }
}
