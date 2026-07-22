package com.loopers.interfaces.api.catalog.ranking;

import com.loopers.application.catalog.ranking.RankingQuery;
import com.loopers.application.catalog.ranking.RankingQueryService;
import com.loopers.application.catalog.ranking.RankingResult;
import com.loopers.domain.catalog.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.support.HeaderValidator;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId RANKING_ZONE = ZoneId.of("Asia/Seoul");

    private final RankingQueryService rankingQueryService;

    @GetMapping
    public ApiResponse<PageResponse<RankingDto.RankingListItemResponse>> getRankings(
        @RequestParam(defaultValue = "daily") String period,
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestHeader(value = HeaderValidator.LOGIN_ID, required = false) String loginId
    ) {
        PageResult<RankingResult> result = rankingQueryService.getRankings(
            new RankingQuery.Search(parseDate(date), parsePeriod(period), page, size, loginId)
        );

        return ApiResponse.success(PageResponse.from(result, RankingDto.RankingListItemResponse::from));
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(RANKING_ZONE);
        }

        try {
            return LocalDate.parse(date, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "랭킹 조회 날짜는 yyyyMMdd 형식이어야 합니다.");
        }
    }

    private RankingPeriod parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return RankingPeriod.DAILY;
        }

        try {
            return RankingPeriod.valueOf(period.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "랭킹 기간은 daily, weekly, monthly 중 하나여야 합니다.");
        }
    }
}
