package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingPageInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {

  private final RankingFacade rankingFacade;

  @GetMapping
  public ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
      @RequestParam(value = "date", required = false) String date,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    RankingPageInfo pageInfo = rankingFacade.getRankings(date, page, size);
    return ApiResponse.success(RankingV1Dto.RankingPageResponse.from(pageInfo));
  }

  @GetMapping("/hourly")
  public ApiResponse<RankingV1Dto.RankingPageResponse> getHourlyRankings(
      @RequestParam(value = "datetime") String dateTime,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    RankingPageInfo pageInfo = rankingFacade.getHourlyRankings(dateTime, page, size);
    return ApiResponse.success(RankingV1Dto.RankingPageResponse.from(pageInfo));
  }
}
