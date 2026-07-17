package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.OptionalLong;

public interface RankingRepository {

  RankingPage findPage(LocalDate date, int page, int size);

  RankingPage findHourlyPage(LocalDateTime dateTime, int page, int size);

  OptionalLong findRank(LocalDate date, Long productId);
}
