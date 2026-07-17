package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingCarryOverKeyPolicy;
import com.loopers.domain.ranking.RankingCarryOverKeys;
import com.loopers.domain.ranking.RankingCarryOverRepository;
import com.loopers.domain.ranking.RankingCarryOverResult;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RankingCarryOverService {

  public static final double CARRY_OVER_RATIO = 0.1D;
  public static final long CARRY_OVER_TTL_SECONDS = 172_800L;

  private final RankingCarryOverRepository carryOverRepository;

  public RankingCarryOverResult carryOver(Instant executionTime) {
    RankingCarryOverKeys keys = RankingCarryOverKeyPolicy.keys(executionTime);
    return carryOverRepository.carryOver(keys, CARRY_OVER_RATIO, CARRY_OVER_TTL_SECONDS);
  }
}
