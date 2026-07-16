package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "상품 일간 랭킹 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(summary = "일간 랭킹 조회",
        description = "일간(yyyyMMdd) 랭킹을 점수 내림차순 페이지로 조회합니다. date 미지정 시 오늘(KST), page 는 1부터.")
    ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(String date, int page, int size);
}
