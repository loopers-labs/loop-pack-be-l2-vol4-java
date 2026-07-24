package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private final RankingFacade rankingFacade;

    @GetMapping
    @Override
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
        @RequestParam(value = "date", required = false)
        @DateTimeFormat(pattern = "yyyyMMdd") LocalDate date,
        @RequestParam(value = "period", required = false, defaultValue = "DAILY") String period,
        @RequestParam(value = "page", required = false, defaultValue = "1") int page,
        @RequestParam(value = "size", required = false, defaultValue = "20") int size
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return ApiResponse.success(RankingV1Dto.RankingPageResponse.from(
            rankingFacade.getRankings(RankingPeriod.from(period), targetDate, page, size)));
    }
}
