package com.loopers.ranking.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "Loopers 상품 랭킹 조회 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(
            summary = "랭킹 페이지 조회",
            description = "date(yyyyMMdd, 생략 시 오늘)의 상품 랭킹을 page/size 로 조회합니다. "
                    + "서빙 창(최근 2일) 밖 날짜는 404, Redis 장애 시 좋아요순 폴백(degraded=true)으로 응답합니다."
    )
    ApiResponse<RankingV1Response.Page> getRankings(String date, int page, int size);
}
