package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking API", description = "상품 랭킹 조회 API")
public interface RankingV1ApiSpec {

    @Operation(
        summary = "랭킹 페이지 조회",
        description = "지정한 날짜가 속한 기간(period)의 상품 랭킹을 점수 내림차순으로 페이징하여 반환합니다. "
            + "period 는 daily|weekly|monthly 이며 생략 시 daily(일간)입니다. daily 는 실시간 랭킹판, "
            + "weekly/monthly 는 배치가 적재한 집계 뷰에서 제공합니다. 각 항목은 1-based 순위와 상품정보(이름·가격 등)를 함께 제공합니다. "
            + "date(yyyyMMdd) 는 필수이며, date 형식·정의되지 않은 period 는 400 을 반환합니다. page 는 0-based 입니다."
    )
    ApiResponse<RankingV1Dto.RankingPageResponse> getRankingPage(String period, String date, int page, int size);
}
