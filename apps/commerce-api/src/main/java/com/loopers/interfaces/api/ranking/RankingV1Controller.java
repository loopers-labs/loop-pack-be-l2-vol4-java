package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingItemInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {

    private final RankingFacade rankingFacade;

    @GetMapping
    public ApiResponse<Page<RankingV1Dto.RankingItemResponse>> getRankings(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(pattern = "yyyyMMdd") final LocalDate date,
            @PageableDefault(size = 20) final Pageable pageable
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        Page<RankingItemInfo> infos = rankingFacade.getRankings(targetDate, pageable);
        Page<RankingV1Dto.RankingItemResponse> response = infos.map(RankingV1Dto.RankingItemResponse::from);
        return ApiResponse.success(response);
    }
}
