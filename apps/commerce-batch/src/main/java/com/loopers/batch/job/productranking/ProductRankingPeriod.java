package com.loopers.batch.job.productranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum ProductRankingPeriod {
  WEEKLY("mv_product_rank_weekly"),
  MONTHLY("mv_product_rank_monthly");

  private final String materializedViewTable;

  ProductRankingPeriod(String materializedViewTable) {
    this.materializedViewTable = materializedViewTable;
  }

  public LocalDate startDate(LocalDate targetDate) {
    return switch (this) {
      case WEEKLY -> targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      case MONTHLY -> targetDate.withDayOfMonth(1);
    };
  }

  String materializedViewTable() {
    return materializedViewTable;
  }
}
