package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "상품 랭킹 API 입니다. (일간/주간/월간)")
public interface RankingV1ApiSpec {

    @Operation(summary = "랭킹 조회",
        description = """
            기준일(date, yyyyMMdd)이 속한 구간의 랭킹을 점수 내림차순 페이지로 조회합니다.
            period 는 DAILY(기본)|WEEKLY|MONTHLY 이며, 일간은 실시간 집계를, 주간/월간은 배치가 만든
            사전 집계(MV)를 조회합니다. date 미지정 시 오늘(KST), page 는 1부터.""")
    ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(String period, String date, int page, int size);
}
