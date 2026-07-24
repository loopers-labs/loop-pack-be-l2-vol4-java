package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ranking.dto.RankingV1Response;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
@Validated
public class RankingV1Controller implements RankingV1ApiSpec {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingFacade rankingFacade;

    @GetMapping
    @Override
    public ApiResponse<Page<RankingV1Response>> getRankings(
        @RequestParam String date,
        @RequestParam(required = false) Integer hour,
        @RequestParam(required = false, defaultValue = "DAILY") String period,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        RankingPeriod rankingPeriod = RankingPeriod.from(period);
        Page<RankingV1Response> result = rankingFacade.getRankingPage(rankingPeriod, parseDate(date), hour, page, size)
            .map(RankingV1Response::from);
        return ApiResponse.success(result);
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date, DATE);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date는 yyyyMMdd 형식이어야 합니다.");
        }
    }
}
