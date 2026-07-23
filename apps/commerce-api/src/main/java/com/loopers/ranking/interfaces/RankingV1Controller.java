package com.loopers.ranking.interfaces;

import com.loopers.ranking.application.RankingFacade;
import com.loopers.ranking.application.RankingInfo;
import com.loopers.ranking.domain.RankPeriod;
import com.loopers.support.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingFacade rankingFacade;

    @GetMapping
    public ApiResponse<List<RankingV1Dto.RankingResponse>> getRankings(
        @RequestParam(required = false) String period,
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        RankPeriod rankPeriod = RankPeriod.from(period);
        LocalDate targetDate = (date == null || date.isBlank())
            ? LocalDate.now()
            : LocalDate.parse(date, YYYYMMDD);
        List<RankingInfo> rankings = rankingFacade.getRankings(rankPeriod, targetDate, page, size);
        return ApiResponse.success(rankings.stream().map(RankingV1Dto.RankingResponse::from).toList());
    }

    // 시간 단위(지난 1시간) 실시간 랭킹 — 날짜 없이 롤링 스냅샷 하나만 조회한다(H6: 별도 엔드포인트)
    @GetMapping("/hourly")
    public ApiResponse<List<RankingV1Dto.RankingResponse>> getHourlyRankings(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<RankingInfo> rankings = rankingFacade.getHourlyRankings(page, size);
        return ApiResponse.success(rankings.stream().map(RankingV1Dto.RankingResponse::from).toList());
    }
}
