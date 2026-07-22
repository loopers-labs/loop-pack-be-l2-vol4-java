package com.loopers.domain.ranking;

import java.util.Objects;

/**
 * 랭킹 순위(VO). 유저 표기 그대로 1-based 등수 값을 담고 불변식 value>=1 만 지킨다.
 * "0-based" 는 Redis ZREVRANK 의 우연이므로 이 도메인 값은 알지 않는다 — +1 변환은 어댑터의 몫.
 */
public final class Rank {

    private final long value;

    private Rank(long value) {
        if (value < 1) {
            throw new IllegalArgumentException("순위는 1 이상이어야 합니다: " + value);
        }
        this.value = value;
    }

    public static Rank of(long value) {
        return new Rank(value);
    }

    public long value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Rank other)) {
            return false;
        }
        return value == other.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
