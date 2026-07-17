package com.loopers.domain.ranking;

public record RankingCarryOverResult(Status status, long carriedProductCount) {

  public enum Status {
    COMPLETED,
    SOURCE_NOT_FOUND,
    ALREADY_COMPLETED
  }

  public static RankingCarryOverResult completed(long carriedProductCount) {
    return new RankingCarryOverResult(Status.COMPLETED, carriedProductCount);
  }

  public static RankingCarryOverResult sourceNotFound() {
    return new RankingCarryOverResult(Status.SOURCE_NOT_FOUND, 0L);
  }

  public static RankingCarryOverResult alreadyCompleted() {
    return new RankingCarryOverResult(Status.ALREADY_COMPLETED, 0L);
  }
}
