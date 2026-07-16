package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking API", description = "상품 랭킹 조회 API")
public interface RankingV1ApiSpec {

    @Operation(
        summary = "랭킹 페이지 조회",
        description = "지정한 날짜의 일별 상품 랭킹을 점수 내림차순으로 페이징하여 반환합니다. "
            + "각 항목은 1-based 순위와 상품정보(이름·가격 등)를 함께 제공합니다. "
            + "date(yyyyMMdd) 는 필수이며, 누락되거나 형식이 잘못되면 400 을 반환합니다. page 는 0-based 입니다."
    )
    ApiResponse<RankingV1Dto.RankingPageResponse> getRankingPage(String date, int page, int size);
}
