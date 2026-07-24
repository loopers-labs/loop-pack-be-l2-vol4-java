package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingDateRange;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingPeriodPolicy;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.stereotype.Component;

@Component
public class ProductRankingJobParameterValidator implements JobParametersValidator {

    private static final String PERIOD = "period";
    private static final String START_DATE = "startDate";
    private static final String END_DATE = "endDate";

    private final RankingPeriodPolicy rankingPeriodPolicy = new RankingPeriodPolicy();

    @Override
    public void validate(JobParameters parameters) throws JobParametersInvalidException {
        try {
            validateRange(parameters);
        } catch (IllegalArgumentException e) {
            throw new JobParametersInvalidException(e.getMessage());
        }
    }

    public RankingDateRange validateRange(JobParameters jobParameters) {
        return parseRange(jobParameters);
    }

    public RankingDateRange parseRange(JobParameters jobParameters) {
        return doValidate(jobParameters);
    }

    private RankingDateRange doValidate(JobParameters jobParameters) {
        String periodValue = requireString(jobParameters, PERIOD);
        String startDate = requireString(jobParameters, START_DATE);
        String endDate = requireString(jobParameters, END_DATE);

        RankingPeriod period = parsePeriod(periodValue);
        return rankingPeriodPolicy.validate(period, startDate, endDate);
    }

    private String requireString(JobParameters jobParameters, String name) {
        String value = jobParameters.getString(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }

    private RankingPeriod parsePeriod(String periodValue) {
        try {
            return RankingPeriod.valueOf(periodValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("period must be DAILY, WEEKLY, or MONTHLY.", e);
        }
    }
}
