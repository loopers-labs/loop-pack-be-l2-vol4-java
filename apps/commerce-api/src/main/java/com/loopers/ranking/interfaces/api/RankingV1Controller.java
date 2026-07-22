package com.loopers.ranking.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.ranking.application.RankingQueryService;
import com.loopers.ranking.application.RankingResult;
import com.loopers.ranking.domain.RankingErrorCode;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final RankingQueryService rankingQueryService;

    @GetMapping
    @Override
    public ApiResponse<RankingV1Response.Page> getRankings(
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        LocalDate target = parseDate(date);
        RankingResult.Page result = rankingQueryService.getRankingPage(target, page, size);
        return ApiResponse.success(RankingV1Response.Page.from(result));
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(SEOUL);
        }
        try {
            return LocalDate.parse(date, YYYYMMDD);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, RankingErrorCode.RANKING_INVALID_DATE);
        }
    }
}
