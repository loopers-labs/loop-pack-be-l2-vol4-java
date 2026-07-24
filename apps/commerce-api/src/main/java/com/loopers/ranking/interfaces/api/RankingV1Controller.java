package com.loopers.ranking.interfaces.api;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.application.RankingFacade;
import com.loopers.ranking.application.RankingItemInfo;
import com.loopers.shared.pagination.PageResult;
import com.loopers.shared.presentation.ApiResponse;
import com.loopers.shared.presentation.PageResponse;
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
    public ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>> getRankings(
        @RequestParam(defaultValue = "DAILY") RankingPeriod period,
        @RequestParam String date,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        RankingV1Dto.RankingRequest request = new RankingV1Dto.RankingRequest(
            period,
            date,
            page,
            size
        );
        PageResult<RankingItemInfo> rankings = rankingFacade.getRankings(
            request.period(),
            request.rankingDate(),
            request.page(),
            request.size()
        );
        return ApiResponse.success(PageResponse.from(rankings.map(RankingV1Dto.RankingItemResponse::from)));
    }
}
