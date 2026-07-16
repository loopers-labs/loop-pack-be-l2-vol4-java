package com.loopers.domain.ranking;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 일별 랭킹 키(VO). ranking:all:{yyyyMMdd} 포맷과 TTL(2일) 의미를 캡슐화한다.
 * 문자열 키는 컴파일러가 검증하지 못하므로 양 앱에 이 VO 를 중복 정의하고,
 * 각 앱의 canary 테스트가 포맷 드리프트를 잡는다. guide 결정 #5.
 */
public final class RankingKey {

    private static final String PREFIX = "ranking:all:";
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration TTL = Duration.ofDays(2);

    private final String value;

    private RankingKey(String value) {
        this.value = value;
    }

    public static RankingKey of(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("랭킹 키 날짜는 null 일 수 없습니다.");
        }
        return new RankingKey(PREFIX + date.format(YYYYMMDD));
    }

    public String value() {
        return value;
    }

    public Duration ttl() {
        return TTL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RankingKey other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
