package com.loopers.batch.job.ranking;

import com.loopers.ranking.RankingPeriod;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;

import java.time.Clock;
import java.time.LocalDate;

public class PeriodRankingJobParametersValidator implements JobParametersValidator {
    private final RankingPeriod period;
    private final Clock clock;

    public PeriodRankingJobParametersValidator(RankingPeriod period, Clock clock) {
        this.period = period;
        this.clock = clock;
    }

    @Override
    public void validate(JobParameters parameters) throws JobParametersInvalidException {
        try {
            LocalDate requestDate = PeriodRankingJobSupport.requestDate(parameters.getString("requestDate"));
            if (!period.isCompleted(requestDate, LocalDate.now(clock))) {
                throw new IllegalArgumentException("requestDate가 속한 기간이 아직 종료되지 않았습니다.");
            }
        } catch (IllegalArgumentException exception) {
            throw new JobParametersInvalidException(exception.getMessage());
        }
    }
}
