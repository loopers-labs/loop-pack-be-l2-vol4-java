package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "일간 상품 랭킹 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(
            summary = "일간 랭킹 페이지 조회",
            description = "date(yyyyMMdd, 생략 시 오늘)의 랭킹을 점수 내림차순으로 페이징 반환합니다. "
                    + "상품 정보가 조립되어 반환되며, 삭제된 상품은 제외되어 페이지가 size 미만일 수 있습니다(순위 gap 유지)."
    )
    ApiResponse<RankingV1Dto.PageResponse> getRankings(String date, int page, int size);
}
