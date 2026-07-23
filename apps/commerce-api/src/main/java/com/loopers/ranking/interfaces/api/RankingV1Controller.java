package com.loopers.ranking.interfaces.api;

import com.loopers.common.interfaces.api.ApiResponse;
import com.loopers.ranking.application.RankingFacade;
import com.loopers.ranking.application.RankingPageInfo;
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
            @RequestParam(value = "period", required = false, defaultValue = "daily")
                    String period,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        RankingPageInfo pageInfo = rankingFacade.getRankings(period, date, page, size);
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
