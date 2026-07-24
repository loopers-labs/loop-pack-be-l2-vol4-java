package com.loopers.batch.job.ranking;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.stereotype.Component;

@Component
public class ProductRankingCleanupJobParameterValidator
    implements JobParametersValidator {

    @Override
    public void validate(JobParameters parameters)
        throws JobParametersInvalidException {
        try {
            ProductRankingCleanupJobParameters.from(parameters);
        } catch (IllegalArgumentException exception) {
            throw new JobParametersInvalidException(exception.getMessage());
        }
    }
}
