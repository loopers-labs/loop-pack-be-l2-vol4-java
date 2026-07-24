package com.loopers.batch.job.ranking;

import com.loopers.domain.ranking.batch.RankingBatchJobParameters;
import jakarta.annotation.Nonnull;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.stereotype.Component;

/**
 * rankingProductMvJob의 period/periodKey 파라미터를 검증한다. 형식이 잘못됐거나
 * 지원하지 않는 period면 Job 실행 전에 즉시 실패시킨다.
 */
@Component
public class RankingBatchJobParametersValidator implements JobParametersValidator {

    @Override
    public void validate(@Nonnull JobParameters parameters) throws JobParametersInvalidException {
        String period = parameters.getString("period");
        String periodKey = parameters.getString("periodKey");
        try {
            RankingBatchJobParameters.of(period, periodKey);
        } catch (IllegalArgumentException e) {
            throw new JobParametersInvalidException(e.getMessage());
        }
    }
}
