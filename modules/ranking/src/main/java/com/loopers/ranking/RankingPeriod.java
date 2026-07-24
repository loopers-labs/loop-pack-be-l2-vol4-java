package com.loopers.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum RankingPeriod {
    DAILY {
        @Override
        public DateRange rangeOf(LocalDate requestDate) {
            return new DateRange(requestDate, requestDate);
        }
    },
    WEEKLY {
        @Override
        public DateRange rangeOf(LocalDate requestDate) {
            LocalDate start = requestDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new DateRange(start, start.plusDays(6));
        }
    },
    MONTHLY {
        @Override
        public DateRange rangeOf(LocalDate requestDate) {
            return new DateRange(
                requestDate.with(TemporalAdjusters.firstDayOfMonth()),
                requestDate.with(TemporalAdjusters.lastDayOfMonth())
            );
        }
    };

    public abstract DateRange rangeOf(LocalDate requestDate);

    public boolean isCompleted(LocalDate requestDate, LocalDate today) {
        return rangeOf(requestDate).end().isBefore(today);
    }

    public record DateRange(LocalDate start, LocalDate end) {
    }
}
