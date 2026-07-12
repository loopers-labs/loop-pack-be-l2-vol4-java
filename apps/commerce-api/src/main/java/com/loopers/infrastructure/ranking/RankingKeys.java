package com.loopers.infrastructure.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 일별 랭킹 ZSET 의 키를 만든다.
 * commerce-streamer(쓰기)의 동명 클래스와 정확히 같은 포맷을 유지해야 한다 —
 * 앱 간 공유 모듈이 없어 각자 복제하되(week9 qna 참고), 포맷이 어긋나면 랭킹 조회가 항상 빈 값이 된다.
 */
public class RankingKeys {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PREFIX = "ranking:all:";

    private RankingKeys() {
    }

    public static String of(LocalDate date) {
        return PREFIX + date.format(DATE_FORMAT);
    }

    public static String today() {
        return of(LocalDate.now());
    }
}
