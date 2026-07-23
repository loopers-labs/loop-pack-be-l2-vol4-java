package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 인기상품 랭킹 API — period로 일간/주간/월간을 조회한다.
 * DAILY(기본)는 commerce-streamer가 적재한 일간 ZSET, WEEKLY/MONTHLY는 commerce-batch가 적재한 MV(최신 스냅샷)에서 조회한다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {

    private final RankingFacade rankingFacade;

    /**
     * 랭킹 Page 조회.
     * period 미지정 시 DAILY(하위호환). DAILY는 date로 대상 날짜 지정(미지정 시 오늘, TTL 2일이라 전일까지 조회 가능),
     * WEEKLY/MONTHLY는 date를 포함하는 스냅샷(미지정 시 최신)을 반환한다.
     */
    @GetMapping
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
        @RequestParam(required = false) String period,
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        RankingPeriod rankingPeriod = parsePeriodOrDaily(period);
        LocalDate targetDate = parseDateOrNull(date);
        return ApiResponse.success(
            RankingV1Dto.RankingPageResponse.from(rankingFacade.getRankings(rankingPeriod, targetDate, page, size))
        );
    }

    private RankingPeriod parsePeriodOrDaily(String period) {
        if (period == null || period.isBlank()) {
            return RankingPeriod.DAILY;
        }
        try {
            return RankingPeriod.valueOf(period.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "period는 DAILY, WEEKLY, MONTHLY 중 하나여야 합니다.");
        }
    }

    /** date 미지정 시 null(파사드가 DAILY는 오늘, WEEKLY/MONTHLY는 최신 스냅샷으로 처리). 형식 오류는 400. */
    private LocalDate parseDateOrNull(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date는 yyyyMMdd 형식이어야 합니다.");
        }
    }
}
