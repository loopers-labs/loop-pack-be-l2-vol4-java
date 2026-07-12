package com.loopers.infrastructure.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 일별 랭킹 ZSET 의 키를 만든다.
 * 이벤트의 발생 시각(occurredAt)이 아니라 컨슈머가 처리하는 시각을 기준으로 날짜를 정한다 —
 * 자정 근처 컨슈머 지연 시 극소수 이벤트가 하루 어긋나게 반영될 수 있으나,
 * 랭킹은 SoT 가 아닌 파생 뷰라 감수 가능한 오차로 판단했다 (week9 qna 참고).
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
