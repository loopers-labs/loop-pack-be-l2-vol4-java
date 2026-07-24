package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingPageInfo;
import com.loopers.domain.ranking.RankingPeriod;
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
 * 인기상품(랭킹) API. 일간·주간·월간 랭킹을 페이지 단위로 노출한다.
 *
 * <p>GET {@code /api/v1/rankings?period=WEEKLY&date=yyyyMMdd&size=20&page=1}
 * <ul>
 *   <li>{@code period} — {@code DAILY}(기본) / {@code WEEKLY} / {@code MONTHLY}. 대소문자 무관.</li>
 *   <li>{@code date} — <b>조회 기준일</b>. 생략 시 오늘(KST). 주간/월간이면 그 날짜가 <b>속한 기간</b>을 찾는다
 *       (예: {@code period=WEEKLY&date=20260723} → 그 주 월요일~일요일).</li>
 * </ul>
 *
 * <p>{@code period} 를 생략하면 기존과 동일하게 일간이라 하위 호환이 유지된다.
 *
 * <p><b>주간/월간은 확정된 기간만 조회된다</b> — 배치가 직전 기간을 집계해 MV 에 올리므로, 진행 중인
 * 이번 주/이번 달을 요청하면 아직 적재 전이라 빈 결과가 된다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RankingFacade rankingFacade;

    @GetMapping
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(pattern = "yyyyMMdd") LocalDate date,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        RankingPeriod rankingPeriod = RankingPeriod.from(period);
        LocalDate target = (date != null) ? date : LocalDate.now(KST);
        RankingPageInfo info = rankingFacade.getRanking(rankingPeriod, target, page, size);
        return ApiResponse.success(RankingV1Dto.RankingPageResponse.from(rankingPeriod, target, info));
    }
}
