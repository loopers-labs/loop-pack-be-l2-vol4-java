package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingPageInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 오늘의 인기상품(랭킹) API. 일간 랭킹 ZSET 을 페이지 단위로 노출한다.
 *
 * <p>GET {@code /api/v1/rankings?date=yyyyMMdd&size=20&page=1} — {@code date} 생략 시 오늘(KST).
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RankingFacade rankingFacade;

    @GetMapping
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(pattern = "yyyyMMdd") LocalDate date,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        LocalDate target = (date != null) ? date : LocalDate.now(KST);
        RankingPageInfo info = rankingFacade.getRanking(target, page, size);
        return ApiResponse.success(RankingV1Dto.RankingPageResponse.from(target, info));
    }
}
