package com.loopers.batch.job.ranking;

import com.loopers.ranking.RankingPeriod;

import java.time.LocalDate;

final class PeriodRankingJobSupport {
    static final int CHUNK_SIZE = 100;

    private PeriodRankingJobSupport() {
    }

    static LocalDate requestDate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("requestDate job parameter는 필수입니다.");
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("requestDate는 yyyy-MM-dd 형식이어야 합니다.", exception);
        }
    }

    static String runKey(RankingPeriod period, LocalDate requestDate, long jobInstanceId) {
        return period.name() + ":" + period.rangeOf(requestDate).start() + ":" + jobInstanceId;
    }
}
