package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {

    private final RankingApplicationService rankingApplicationService;

    @GetMapping
    public ApiResponse<List<RankingV1Dto.RankingResponse>> getRankings(
        @RequestParam @DateTimeFormat(pattern = "yyyyMMdd") LocalDate date,
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        List<RankingV1Dto.RankingResponse> responses = rankingApplicationService.getRankings(date, page, size).stream()
            .map(RankingV1Dto.RankingResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
