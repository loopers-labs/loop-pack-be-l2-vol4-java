package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingPageInfo;
import com.loopers.domain.ranking.RankingHourlyQueryCondition;
import com.loopers.domain.ranking.RankingMvQueryCondition;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingQueryCondition;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
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
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // yyyyMMddHH만으로는 분/초 필드가 없어 LocalDateTime으로 바로 파싱할 수 없으므로 0으로 채워 넣는다.
    private static final DateTimeFormatter DATE_TIME_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyyMMddHH")
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter();

    private final RankingFacade rankingFacade;

    @GetMapping
    public ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>> getRankings(
        @RequestParam(value = "date", required = false) String date,
        @RequestParam(value = "period", required = false) String period,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        RankingPeriod rankingPeriod = RankingPeriod.from(period);
        LocalDate parsedDate = parseDate(date);
        // DAILY 는 실시간 Redis, WEEKLY/MONTHLY 는 배치가 만든 MV 를 조회한다.
        RankingPageInfo pageInfo = (rankingPeriod == RankingPeriod.DAILY)
                ? rankingFacade.getRankings(new RankingQueryCondition(parsedDate, page, size))
                : rankingFacade.getMvRankings(new RankingMvQueryCondition(rankingPeriod, parsedDate, page, size));
        List<RankingV1Dto.RankingItemResponse> content =
                pageInfo.items().stream().map(RankingV1Dto.RankingItemResponse::from).toList();
        return ApiResponse.success(PageResponse.of(content, pageInfo.totalElements(), page, size));
    }

    @GetMapping("/hourly")
    public ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>> getHourlyRankings(
        @RequestParam(value = "dateTime", required = false) String dateTime,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        RankingHourlyQueryCondition condition = new RankingHourlyQueryCondition(parseDateTime(dateTime), page, size);
        RankingPageInfo pageInfo = rankingFacade.getHourlyRankings(condition);
        List<RankingV1Dto.RankingItemResponse> content =
                pageInfo.items().stream().map(RankingV1Dto.RankingItemResponse::from).toList();
        return ApiResponse.success(PageResponse.of(content, pageInfo.totalElements(), page, size));
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date는 yyyyMMdd 형식이어야 합니다.");
        }
    }

    private LocalDateTime parseDateTime(String dateTime) {
        if (dateTime == null || dateTime.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(dateTime, DATE_TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "dateTime은 yyyyMMddHH 형식이어야 합니다.");
        }
    }
}
