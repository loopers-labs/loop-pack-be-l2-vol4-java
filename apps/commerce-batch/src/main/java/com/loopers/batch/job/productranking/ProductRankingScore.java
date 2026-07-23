package com.loopers.batch.job.productranking;

import java.math.BigDecimal;

public record ProductRankingScore(long productId, BigDecimal score) {}
