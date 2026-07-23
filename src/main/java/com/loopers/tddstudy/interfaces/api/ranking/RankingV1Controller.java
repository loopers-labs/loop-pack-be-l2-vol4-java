package com.loopers.tddstudy.interfaces.api.ranking;

import com.loopers.tddstudy.application.ranking.RankingInfo;
import com.loopers.tddstudy.application.ranking.RankingService;
import com.loopers.tddstudy.domain.ranking.RankingPeriod;
import com.loopers.tddstudy.domain.ranking.RankingPeriodType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final RankingService rankingService;

    public RankingV1Controller(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    // GET /api/v1/rankings?period=WEEKLY&date=20260706&size=20&page=1
    @GetMapping
    public ResponseEntity<RankingV1Dto.RankingResponse> getRankings(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "DAILY") RankingPeriodType period,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        LocalDate targetDate = (date == null)
                ? LocalDate.now(ZONE)                       // 미지정 → 오늘 기준
                : LocalDate.parse(date, DATE_FORMAT);

        List<RankingInfo> items = rankingService.getRankingPage(period, targetDate, page, size);

        return ResponseEntity.ok(new RankingV1Dto.RankingResponse(
                targetDate.format(DATE_FORMAT),
                period.name(),
                periodKeyOf(period, targetDate),
                page, size,
                items.stream().map(RankingV1Dto.Item::from).toList()
        ));
    }

    // 응답에 실제로 어떤 기간이 조회됐는지 알려주기 위한 키
    private static String periodKeyOf(RankingPeriodType period, LocalDate date) {
        return switch (period) {
            case DAILY   -> date.format(DATE_FORMAT);
            case WEEKLY  -> RankingPeriod.lastCompletedWeek(date).key();
            case MONTHLY -> RankingPeriod.lastCompletedMonth(date).key();
        };
    }
}
