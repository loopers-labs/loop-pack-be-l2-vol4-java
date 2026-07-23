package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingApplicationService;
import com.loopers.application.ranking.RankingInfo;
import com.loopers.domain.common.PageResult;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    /** ZREVRANGE 범위 상한 — 무제한 size 로 보드 전체 스캔을 막는다. */
    private static final int MAX_SIZE = 100;

    private final RankingApplicationService rankingApplicationService;

    @GetMapping
    @Override
    public ApiResponse<RankingV1Dto.PageResponse> getRankings(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        LocalDate targetDate = RankingV1Dto.parseDateOrToday(date);
        int clampedSize = Math.min(Math.max(size, 1), MAX_SIZE);
        int clampedPage = Math.max(page, 0);
        PageResult<RankingInfo.RankedItem> result =
                rankingApplicationService.getRankings(targetDate, clampedPage, clampedSize);
        return ApiResponse.success(RankingV1Dto.PageResponse.from(targetDate, result));
    }
}
