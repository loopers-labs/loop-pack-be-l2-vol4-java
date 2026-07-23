package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {
    private final RankingFacade rankingFacade;
    private final Clock clock;

    @GetMapping
    public ApiResponse<List<RankingDto.Response>> getRankings(
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate date,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        if (page < 1 || size < 1 || size > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상, size는 1 이상 100 이하여야 합니다.");
        }
        LocalDate targetDate = date == null ? LocalDate.now(clock) : date;
        return ApiResponse.success(
            rankingFacade.getRankings(targetDate, page, size).stream()
                .map(RankingDto.Response::from)
                .toList()
        );
    }
}
