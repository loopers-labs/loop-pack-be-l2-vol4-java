package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingProductInfo;
import com.loopers.domain.ranking.RankingDateRange;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingPeriodPolicy;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {

    private static final DateTimeFormatter DATE_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingFacade rankingFacade;
    private final RankingPeriodPolicy rankingPeriodPolicy = new RankingPeriodPolicy();

    @GetMapping
    public ApiResponse<PageResponse<RankingV1Dto.RankingResponse>> getRankings(
        @RequestParam(value = "period", defaultValue = "DAILY") RankingPeriod period,
        @RequestParam(value = "date", required = false) String date,
        @RequestParam(value = "startDate", required = false) String startDate,
        @RequestParam(value = "endDate", required = false) String endDate,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        RankingDateRange dateRange = resolveDateRange(period, date, startDate, endDate);
        Page<RankingProductInfo> rankingPage = rankingFacade.getRankings(
            dateRange.period(),
            dateRange.startDate().format(DATE_KEY_FORMATTER),
            dateRange.endDate().format(DATE_KEY_FORMATTER),
            page,
            size
        );
        Page<RankingV1Dto.RankingResponse> responsePage = rankingPage.map(RankingV1Dto.RankingResponse::from);
        return ApiResponse.success(PageResponse.from(responsePage));
    }

    private RankingDateRange resolveDateRange(
        RankingPeriod period,
        String date,
        String startDate,
        String endDate
    ) {
        try {
            if (startDate != null || endDate != null) {
                return rankingPeriodPolicy.validate(period, startDate, endDate);
            }
            if (period != RankingPeriod.DAILY) {
                throw new IllegalArgumentException("startDate and endDate are required for WEEKLY or MONTHLY rankings.");
            }

            String dateKey = date == null ? LocalDate.now().format(DATE_KEY_FORMATTER) : date;
            return rankingPeriodPolicy.validate(RankingPeriod.DAILY, dateKey, dateKey);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }
}
