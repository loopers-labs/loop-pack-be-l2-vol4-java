package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;

@Tag(name = "Ranking V1 API", description = "Loopers 랭킹 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(
        summary = "랭킹 페이지 조회",
        description = "일자별 상품 랭킹을 페이지 단위로 조회합니다. date 생략 시 오늘 날짜를 기준으로 합니다."
    )
    ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
        @Parameter(name = "date", in = ParameterIn.QUERY, description = "조회할 날짜(yyyyMMdd), 생략 시 오늘")
        LocalDate date,
        @Parameter(name = "page", in = ParameterIn.QUERY, description = "페이지(1부터, 기본 1)")
        int page,
        @Parameter(name = "size", in = ParameterIn.QUERY, description = "페이지 크기(기본 20)")
        int size
    );
}
