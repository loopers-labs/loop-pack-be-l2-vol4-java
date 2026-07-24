package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingApplicationService;
import com.loopers.application.ranking.RankingInfo;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 실시간 상품 랭킹 API. 일간 랭킹(ranking:all:{date})과 시간단위 랭킹(ranking:hour:{dateHour})을
 * 각각 상위 100위까지 페이지 단위로 제공한다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {

    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final RankingApplicationService rankingApplicationService;

    @GetMapping
    public ApiResponse<List<RankingV1Dto.RankingResponse>> getRankings(
        @RequestParam(required = false) String date,
        @RequestParam(required = false) String period,
        @RequestParam(required = false, defaultValue = "20") int size,
        @RequestParam(required = false, defaultValue = "1") int page
    ) {
        LocalDate target = parseDate(date);
        RankingPeriod rankingPeriod = RankingPeriod.from(period);
        List<RankingV1Dto.RankingResponse> responses = rankingApplicationService.getRanking(target, rankingPeriod, page, size).stream()
            .map(RankingV1Dto.RankingResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/hourly")
    public ApiResponse<List<RankingV1Dto.RankingResponse>> getHourlyRankings(
        @RequestParam(required = false) String dateHour,
        @RequestParam(required = false, defaultValue = "20") int size,
        @RequestParam(required = false, defaultValue = "1") int page
    ) {
        LocalDateTime target = parseDateHour(dateHour);
        List<RankingV1Dto.RankingResponse> responses = rankingApplicationService.getHourlyRanking(target, page, size).stream()
            .map(RankingV1Dto.RankingResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    private LocalDate parseDate(String date) {
        if (date == null) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date 는 yyyyMMdd 형식이어야 합니다.");
        }
    }

    private LocalDateTime parseDateHour(String dateHour) {
        if (dateHour == null) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(dateHour + "00", DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "dateHour 는 yyyyMMddHH 형식이어야 합니다.");
        }
    }
}
